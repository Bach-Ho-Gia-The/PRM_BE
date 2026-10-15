package com.structure.springboot.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefreshTokenRedis {

    private static final String PREFIX = "rt:";
    private static final String USER_INDEX_PREFIX = "user_rt:";
    private static final String LOGOUT_TIME_PREFIX = "logout_time:";

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;

    // Lưu Refresh Token với JTI duy nhất
    public void store(String jti, String userId, Duration ttl) {
        redis.opsForValue().set(PREFIX + jti, userId, ttl);

        String indexKey = USER_INDEX_PREFIX + userId;
        redis.opsForSet().add(indexKey, jti);
        redis.expire(indexKey, ttl);
    }

    public Optional<String> getUserIdByJti(String jti) {
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + jti));
    }

    // Thu hồi 1 token cụ thể
    public void revoke(String jti) {
        String userId = redis.opsForValue().get(PREFIX + jti);
        if (userId != null) {
            redis.delete(PREFIX + jti);
            redis.opsForSet().remove(USER_INDEX_PREFIX + userId, jti);
        }
    }

    // Đăng xuất khỏi mọi thiết bị (Logout All)
    public void revokeAllTokens(String userId) {
        String indexKey = USER_INDEX_PREFIX + userId;
        Set<String> jtis = redis.opsForSet().members(indexKey);

        if (jtis != null && !jtis.isEmpty()) {
            List<String> keysToDelete = new ArrayList<>();
            for (String jti : jtis) {
                keysToDelete.add(PREFIX + jti);
            }
            redis.delete(keysToDelete);
            redis.delete(indexKey);
        }
    }

    // Cắm mốc thời gian logout/đổi mật khẩu. TTL = Refresh TTL để key tự dọn, khỏi phình RAM.
    // Dùng getRefreshTtlMs() cho đồng bộ với store(): cùng một nguồn giá trị, cùng một đường fallback.
    public void setLogoutTime(String email) {
        redis.opsForValue().set(
                LOGOUT_TIME_PREFIX + email,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofMillis(jwtProperties.getRefreshTtlMs())
        );
    }

    /**
     * Access token còn hiệu lực hay đã bị thu hồi — gọi trên MỌI request nên chỉ tốn
     * <b>một vòng mạng Redis duy nhất</b> (MGET 2 key), không phải hai lần GET.
     *
     * <p>Hai lý do bị thu hồi:</p>
     * <ol>
     *   <li><b>Mốc logout toàn cục</b> — token phát hành trước khi đổi mật khẩu / logout-all / khoá
     *       tài khoản thì chết, kể cả phiên khác.</li>
     *   <li><b>Phiên đã logout</b> — access token mang claim {@code sid} chính là jti của refresh
     *       token sinh ra nó. Refresh token bị xoá khỏi Redis ⇒ access token chết theo ngay lập tức,
     *       không phải chờ hết 15 phút. Nhờ vậy không cần thêm bảng denylist riêng nào.</li>
     * </ol>
     */
    /**
     * Xoá sạch mọi phiên của một tài khoản. Hai bước bổ trợ nhau, thiếu bước nào cũng hở:
     * <ul>
     *   <li>{@code revokeAllTokens} giết các phiên <b>đang được Redis theo dõi</b>;</li>
     *   <li>{@code setLogoutTime} cắm mốc thời gian, chặn nốt access token nào không mang
     *       claim {@code sid} (token phát hành trước khi có cơ chế này).</li>
     * </ul>
     *
     * <p>Đặt ở đây chứ không để mỗi service tự ghép hai lệnh: logout-all, đổi email, đổi mật khẩu
     * và xoá tài khoản đều cần đúng một thao tác này. Trước đó nó bị chép thành hai bản private
     * giống hệt nhau ở {@code AuthServiceImpl} và {@code AccountServiceImpl} — sửa một bên là lệch.</p>
     */
    public void revokeEverySession(String userId, String email) {
        if (StringUtils.hasText(userId)) {
            revokeAllTokens(userId);
        }
        if (StringUtils.hasText(email)) {
            setLogoutTime(email);
        }
    }

    public boolean isAccessTokenRevoked(String email, String sessionId, long issuedAtMillis) {
        List<String> keys = sessionId == null
                ? List.of(LOGOUT_TIME_PREFIX + email)
                : List.of(LOGOUT_TIME_PREFIX + email, PREFIX + sessionId);

        List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty()) {
            return false;
        }

        String logoutTime = values.get(0);
        if (logoutTime != null && issuedAtMillis < Long.parseLong(logoutTime)) {
            return true;
        }

        // sessionId null = token phát hành trước khi có claim "sid" -> không chặn, tránh đá văng
        // những phiên đang đăng nhập hợp lệ ngay lúc triển khai bản mới.
        return sessionId != null && values.size() > 1 && values.get(1) == null;
    }
}
