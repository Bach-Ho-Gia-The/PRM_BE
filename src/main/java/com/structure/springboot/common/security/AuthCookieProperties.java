package com.structure.springboot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thuộc tính của cookie xác thực, gom từ {@code app.auth.cookie.*}.
 *
 * <p>Trước đây 12 dòng {@code @Value} y hệt nhau nằm rải ở {@code JwtAuthenticationFilter},
 * {@code AuthCookieFactory} và {@code AuthController} — sửa một chỗ quên hai chỗ là cookie
 * ghi ra một kiểu, đọc vào một kiểu. Gom về một nguồn theo đúng tiền lệ {@link JwtProperties}.</p>
 *
 * <p>Compact constructor điền sẵn giá trị dự phòng nên thiếu key trong yaml cũng không NPE,
 * và mọi nơi dùng chỉ việc gọi accessor, không phải tự kiểm tra null.</p>
 */
@ConfigurationProperties(prefix = "app.auth.cookie")
public record AuthCookieProperties(
        String accessTokenName,
        String refreshTokenName,
        boolean secure,
        String sameSite,
        String path
) {
    public AuthCookieProperties {
        accessTokenName = accessTokenName != null ? accessTokenName : "access_token";
        refreshTokenName = refreshTokenName != null ? refreshTokenName : "refresh_token";
        sameSite = sameSite != null ? sameSite : "Lax";
        path = path != null ? path : "/";
    }
}
