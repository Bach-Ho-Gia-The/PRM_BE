package com.structure.springboot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Ngưỡng chặn dò mật khẩu, đọc từ {@code app.security.login-attempt.*}.
 *
 * <p>Đặt cạnh {@link JwtProperties} và {@link AuthCookieProperties} để mọi tham số bảo mật
 * nằm chung một chỗ, không phải đi lục từng file service.</p>
 */
@ConfigurationProperties(prefix = "app.security.login-attempt")
public record LoginAttemptProperties(
        Integer maxAttempts,
        Integer maxAttemptsPerIp,
        Duration lockDuration
) {
    public LoginAttemptProperties {
        // Thiếu key trong yaml vẫn phải có ngưỡng an toàn, không được rơi về "không giới hạn".
        maxAttempts = (maxAttempts != null && maxAttempts > 0) ? maxAttempts : 5;
        // Ngưỡng theo IP phải nới hơn hẳn: văn phòng hay mạng di động dùng chung NAT thì hàng trăm
        // người thật cùng đi ra một IP, siết chặt là chặn nhầm người dùng hợp lệ.
        maxAttemptsPerIp = (maxAttemptsPerIp != null && maxAttemptsPerIp > 0) ? maxAttemptsPerIp : 20;
        lockDuration = lockDuration != null ? lockDuration : Duration.ofMinutes(15);
    }
}
