package com.structure.springboot.features.auth.security;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.LoginAttemptProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * Chặn dò mật khẩu: sai quá ngưỡng thì khoá tạm theo thời gian, trả 429 thay vì cứ cho thử mãi.
 *
 * <p><b>Hai tầng đếm, mỗi tầng bịt một kiểu tấn công:</b></p>
 * <ul>
 *   <li><b>Theo tài khoản</b> (ngưỡng thấp) — chặn kiểu nhắm một tài khoản rồi rải mật khẩu từ
 *       nhiều IP khác nhau; đếm theo IP hoàn toàn bó tay với kiểu này.</li>
 *   <li><b>Theo IP</b> (ngưỡng cao hơn) — chặn kiểu <i>password spraying</i>: lấy một mật khẩu
 *       phổ biến quét qua hàng nghìn tài khoản, mỗi tài khoản chỉ sai một lần nên bộ đếm theo tài khoản
 *       không bao giờ chạm ngưỡng.</li>
 * </ul>
 *
 * <p><b>Vì sao đếm bằng {@code StringRedisTemplate} chứ không qua {@code RedisService}:</b>
 * bộ đếm bắt buộc phải tăng <b>nguyên tử</b> (lệnh {@code INCR} của Redis). {@code RedisService}
 * chỉ có {@code get}/{@code set} — hai lệnh rời nhau, hai request đăng nhập song song sẽ cùng đọc
 * ra 4 rồi cùng ghi 5, đếm mất một lần sai. Ngoài ra {@code RedisService} serialize giá trị thành
 * JSON nên số bị lưu thành chuỗi có dấu nháy, {@code INCR} không chạy được trên đó.
 * {@code RefreshTokenRedis} cũng dùng {@code StringRedisTemplate} trực tiếp vì cùng lý do.</p>
 *
 * <p><b>Đánh đổi đã biết:</b> khoá theo tài khoản nên kẻ xấu cố tình nhập sai có thể khoá tạm
 * tài khoản người khác; khoá theo IP thì cả một văn phòng đi chung NAT có thể bị vạ lây. Cửa sổ
 * khoá để ngắn và ngưỡng IP để cao hơn hẳn chính là để hạn chế hai mặt trái đó.</p>
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

    private final StringRedisTemplate redis;
    private final LoginAttemptProperties properties;

    /** Gọi TRƯỚC khi xác thực. Đang bị khoá thì dừng ngay, khỏi tốn một lần so khớp BCrypt. */
    public void assertNotLocked(String identifier, String clientIp) {
        assertUnderLimit(identifierKey(identifier), properties.maxAttempts());
        assertUnderLimit(ipKey(clientIp), properties.maxAttemptsPerIp());
    }

    /** Gọi khi sai mật khẩu. Tăng cả hai bộ đếm vì mỗi bộ bịt một kiểu tấn công khác nhau. */
    public void recordFailure(String identifier, String clientIp) {
        increment(identifierKey(identifier));
        increment(ipKey(clientIp));
    }

    /**
     * Gọi khi đăng nhập đúng. Chỉ xoá bộ đếm theo tài khoản — <b>cố ý giữ bộ đếm IP</b>: kẻ quét hàng
     * loạt chỉ cần dò trúng một tài khoản là sẽ xoá sạch dấu vết rồi quét tiếp.
     */
    public void reset(String identifier) {
        String key = identifierKey(identifier);
        if (key != null) {
            redis.delete(key);
        }
    }

    private void assertUnderLimit(String key, int maxAttempts) {
        if (key == null) {
            return;
        }

        String attempts = redis.opsForValue().get(key);
        if (attempts == null || parseAttempts(attempts) < maxAttempts) {
            return;
        }

        // Nói rõ còn bao lâu để người dùng thật khỏi phải ngồi đoán; không tiết lộ gì thêm về tài khoản.
        Long secondsLeft = redis.getExpire(key, TimeUnit.SECONDS);
        String waitHint = (secondsLeft != null && secondsLeft > 0)
                ? " Please try again in " + secondsLeft + " seconds."
                : " Please try again later.";

        throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "Too many failed login attempts." + waitHint);
    }

    /** TTL chỉ đặt ở lần sai ĐẦU TIÊN -> cửa sổ cố định, không gia hạn vô tận. */
    private void increment(String key) {
        if (key == null) {
            return;
        }

        Long attempts = redis.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redis.expire(key, properties.lockDuration());
        }
    }

    /**
     * Chuẩn hoá về chữ thường vì đăng nhập so khớp email không phân biệt hoa thường — không có
     * bước này thì "Admin@x.vn" và "admin@x.vn" đếm thành hai bộ riêng, thử được gấp đôi số lần.
     *
     * <p>Tên {@code identifier} chứ không phải {@code username}: giá trị chạy qua đây là chuỗi
     * người dùng gõ vào ô đăng nhập, mà dự án đã chuyển sang đăng nhập bằng email.</p>
     */
    private String identifierKey(String identifier) {
        return StringUtils.hasText(identifier)
                ? "login_fail:" + identifier.trim().toLowerCase()
                : null;
    }

    /** IP rỗng (gọi nội bộ, unit test) thì bỏ qua tầng này thay vì gộp tất cả vào một bộ đếm chung. */
    private String ipKey(String clientIp) {
        return StringUtils.hasText(clientIp)
                ? "login_fail_ip:" + clientIp.trim()
                : null;
    }

    private int parseAttempts(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            // Giá trị rác trong Redis không được phép biến thành khoá vĩnh viễn.
            return 0;
        }
    }
}
