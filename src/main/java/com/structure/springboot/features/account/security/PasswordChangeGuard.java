package com.structure.springboot.features.account.security;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Khoảng chờ giữa hai lần đổi mật khẩu thành công của cùng một tài khoản.
 *
 * <p>Mỗi lần đổi thành công đều kéo theo một email báo tin và một cú thu hồi toàn bộ phiên. Không
 * có khoảng chờ thì ai cầm được token hợp lệ cũng bấm liên tục được, biến hộp thư của chủ tài
 * khoản thành bãi rác và đá họ ra khỏi mọi thiết bị không ngừng nghỉ.</p>
 *
 * <p><b>Chỉ tính trên lần đổi THÀNH CÔNG.</b> Gõ sai mật khẩu hiện tại không bị tính — nếu không
 * thì người dùng thật lỡ tay một lần là phải ngồi chờ vài phút.</p>
 *
 * <p><b>Không áp cho luồng quên mật khẩu.</b> Đường đó đã có cooldown OTP và trần gửi mail theo
 * giờ chặn từ vòng ngoài; chặn thêm ở đây chỉ nhốt người thật đang loay hoay lấy lại tài khoản.</p>
 *
 * <p>Đặt ở {@code features/account/security} soi gương {@code features/auth/security} nơi
 * {@code LoginAttemptGuard} đang nằm: cùng vai trò, khác module, dữ liệu module nào module đó giữ.</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeGuard {

    private static final String KEY_PREFIX = "password_change:";

    private final StringRedisTemplate redis;

    // Một giá trị duy nhất, một nơi dùng duy nhất: gom thành record @ConfigurationProperties chỉ
    // để giữ đúng một field là đi ngược lý do AuthCookieProperties ra đời (gom khi NHIỀU giá trị
    // rải NHIỀU nơi). Dùng @Value thẳng, theo tiền lệ DataInitializer và SecurityConfig.
    @Value("${app.account.password-change-cooldown:5m}")
    private Duration cooldown;

    public void assertNotCoolingDown(UUID accountId) {
        Long secondsLeft = redis.getExpire(KEY_PREFIX + accountId, TimeUnit.SECONDS);

        if (secondsLeft != null && secondsLeft > 0) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "You changed your password recently. Please wait " + secondsLeft
                            + " more seconds before changing it again.");
        }
    }

    public void startCooldown(UUID accountId) {
        redis.opsForValue().set(KEY_PREFIX + accountId, "1", cooldown);
    }
}
