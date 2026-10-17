package com.structure.springboot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tham số của mã OTP khôi phục mật khẩu, đọc từ {@code app.otp.*}.
 *
 * <p>Đặt cạnh {@link JwtProperties}, {@link AuthCookieProperties} và {@link LoginAttemptProperties}
 * theo đúng tiền lệ: mọi tham số bảo mật nằm chung một chỗ.</p>
 *
 * <p>Tiền tố là {@code app.otp} chứ không phải {@code otp} như khối nháp trong yaml: toàn bộ cấu
 * hình do dự án tự định nghĩa đều nằm dưới {@code app.*}, để lẫn ra ngoài là sớm muộn cũng đụng
 * tên với một thuộc tính của thư viện nào đó.</p>
 */
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        Integer ttlSeconds,
        Integer maxAttempts,
        Integer verifiedTtlSeconds,
        Integer maxSendsPerHour,
        Integer registerTtlSeconds,
        Integer resendCooldownSeconds
) {
    public OtpProperties {
        // Thiếu key trong yaml vẫn phải có ngưỡng an toàn, không được rơi về "không giới hạn".
        ttlSeconds = (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : 90;
        maxAttempts = (maxAttempts != null && maxAttempts > 0) ? maxAttempts : 5;
        verifiedTtlSeconds = (verifiedTtlSeconds != null && verifiedTtlSeconds > 0) ? verifiedTtlSeconds : 300;
        maxSendsPerHour = (maxSendsPerHour != null && maxSendsPerHour > 0) ? maxSendsPerHour : 5;
        // Mã đăng ký sống lâu hơn hẳn mã quên mật khẩu: khách phải rời app, mở hộp thư, chờ mail
        // về rồi quay lại gõ. 90 giây như luồng reset là quá gắt cho chuỗi thao tác đó.
        registerTtlSeconds = (registerTtlSeconds != null && registerTtlSeconds > 0) ? registerTtlSeconds : 600;
        // Khoảng chờ giữa hai lần bấm "gửi lại", KHÔNG liên quan tới hạn của mã.
        resendCooldownSeconds = (resendCooldownSeconds != null && resendCooldownSeconds > 0) ? resendCooldownSeconds : 20;
    }
}
