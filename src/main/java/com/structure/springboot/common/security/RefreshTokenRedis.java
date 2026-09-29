package com.structure.springboot.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    // Cắm mốc thời gian logout/đổi mật khẩu (ĐÃ BỔ SUNG TTL = Refresh TTL để tự dọn dẹp RAM)
    public void setLogoutTime(String email) {
        Duration ttl = jwtProperties.refreshTtl(); // Tự hủy sau khi refresh token dài nhất hết hạn
        redis.opsForValue().set(
                LOGOUT_TIME_PREFIX + email,
                String.valueOf(System.currentTimeMillis()),
                ttl
        );
    }

    public Long getLogoutTime(String email) {
        String val = redis.opsForValue().get(LOGOUT_TIME_PREFIX + email);
        return val != null ? Long.parseLong(val) : null;
    }
}
