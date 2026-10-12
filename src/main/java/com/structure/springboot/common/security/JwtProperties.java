package com.structure.springboot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình JWT, nạp từ {@code app.jwt.*}.
 *
 * <p>Giá trị mặc định đặt trong compact constructor — cùng một kiểu với
 * {@link AuthCookieProperties} nằm cạnh bên, thay vì mỗi record chống null theo một cách khác
 * nhau. Nhờ vậy hai getter phía dưới không còn phải kiểm tra null lần nữa.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,   // Khai báo trong yaml ví dụ: 15m, 1h
        Duration refreshTtl   // Khai báo trong yaml ví dụ: 7d, 30d
) {

    public JwtProperties {
        accessTtl = accessTtl != null ? accessTtl : Duration.ofMinutes(15);
        refreshTtl = refreshTtl != null ? refreshTtl : Duration.ofDays(7);
    }

    public long getAccessTtlMs() {
        return accessTtl.toMillis();
    }

    public long getRefreshTtlMs() {
        return refreshTtl.toMillis();
    }
}
