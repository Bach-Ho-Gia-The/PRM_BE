package com.structure.springboot.features.account.security;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.auth.enums.OtpPurpose;
import com.structure.springboot.features.auth.security.OtpRedis;
import com.structure.springboot.common.security.OtpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Trạng thái Redis của luồng đổi email: địa chỉ đang chờ xác minh và mã OTP đi kèm.
 *
 * <p><b>Vì sao khoá theo {@code accountId} chứ không theo email mới:</b> {@link OtpRedis} gắn
 * khoảng chờ và trần gửi theo {@code subject}. Lấy email mới làm subject thì chỉ cần đổi địa chỉ
 * mỗi lần bấm là lách sạch cả hai hàng rào — server thành máy dội mail vào hộp thư người lạ và
 * đốt hết hạn mức Brevo. Khoá theo tài khoản thì một người nhắm bao nhiêu địa chỉ cũng chỉ gửi
 * được đúng số lần cho phép.</p>
 *
 * <p>Địa chỉ đang chờ phải cất riêng vì {@code OtpRedis} chỉ giữ được mã. Hết hạn là yêu cầu tự
 * huỷ, không cần dọn.</p>
 *
 * <p>Đặt ở {@code features/account/security} cạnh {@link PasswordChangeGuard}: cùng vai trò giữ
 * trạng thái bảo mật của module account.</p>
 */
@Component
@RequiredArgsConstructor
public class EmailChangeOtp {

    private static final String PENDING_PREFIX = "email_change:";

    private final StringRedisTemplate redis;
    private final OtpRedis otpRedis;
    private final OtpProperties otpProperties;

    private final SecureRandom random = new SecureRandom();

    /**
     * Chặn bấm liên tục. Khác luồng quên mật khẩu ở chỗ báo lỗi THẲNG thay vì im lặng: người gọi
     * đã đăng nhập nên không có gì để giấu, im lặng chỉ khiến họ bấm mãi mà không hiểu vì sao.
     */
    public void assertCanSend(UUID accountId) {
        String subject = accountId.toString();

        long waiting = otpRedis.cooldownRemaining(OtpPurpose.CHANGE_EMAIL, subject);
        if (waiting > 0) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Please wait " + waiting + " more seconds before requesting another code.");
        }

        if (otpRedis.incrementSendCount(OtpPurpose.CHANGE_EMAIL, subject) > otpProperties.maxSendsPerHour()) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many verification codes requested. Please try again later.");
        }
    }

    /** Sinh mã mới và ghi đè mã cũ; bộ đếm nhập sai được {@code storeCode} xoá kèm. */
    public String issueCode(UUID accountId, Duration ttl) {
        String code = "%06d".formatted(random.nextInt(1_000_000));
        otpRedis.storeCode(OtpPurpose.CHANGE_EMAIL, accountId.toString(), code, ttl);
        return code;
    }

    public void startCooldown(UUID accountId, Duration cooldown) {
        otpRedis.startCooldown(OtpPurpose.CHANGE_EMAIL, accountId.toString(), cooldown);
    }

    public void storePending(UUID accountId, String newEmail, Duration ttl) {
        redis.opsForValue().set(PENDING_PREFIX + accountId, newEmail, ttl);
    }

    public Optional<String> findPending(UUID accountId) {
        return Optional.ofNullable(redis.opsForValue().get(PENDING_PREFIX + accountId));
    }

    public void clearPending(UUID accountId) {
        redis.delete(PENDING_PREFIX + accountId);
    }

    /**
     * Đối chiếu mã người dùng gõ. Hai cổng chặn giống {@code AuthServiceImpl.requireValidOtp}:
     * cổng đầu dừng TRƯỚC khi so để không cho dò bằng số lần vô hạn, cổng sau huỷ mã ngay ở lần
     * sai cuối cùng để người dùng biết liền thay vì bấm thêm một lần nữa mới hay.
     */
    public void requireValidCode(UUID accountId, String submittedCode, Duration ttl) {
        String subject = accountId.toString();
        long attempts = otpRedis.incrementAttempts(OtpPurpose.CHANGE_EMAIL, subject, ttl);

        if (attempts > otpProperties.maxAttempts()) {
            clearAll(accountId);
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please start the email change again.");
        }

        String storedCode = otpRedis.findCode(OtpPurpose.CHANGE_EMAIL, subject)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "The verification code has expired. Please start the email change again."));

        if (storedCode.equals(submittedCode)) {
            otpRedis.clearCode(OtpPurpose.CHANGE_EMAIL, subject);
            return;
        }

        if (attempts >= otpProperties.maxAttempts()) {
            clearAll(accountId);
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please start the email change again.");
        }

        throw new ApiException(ErrorCode.BAD_REQUEST, "Incorrect verification code");
    }

    /** Hết lượt thử thì bỏ luôn địa chỉ đang chờ, không để lại yêu cầu treo không mã nào mở được. */
    private void clearAll(UUID accountId) {
        otpRedis.clearCode(OtpPurpose.CHANGE_EMAIL, accountId.toString());
        clearPending(accountId);
    }
}
