package com.structure.springboot.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Nơi DUY NHẤT sinh ra cookie xác thực — viết một lần, dùng lại cho Login / Refresh / Logout.
 *
 * <p>Nằm ở {@code common/security} chứ không phải {@code features/auth} vì
 * {@code JwtAuthenticationFilter} cũng phải xoá cookie khi token hỏng — mà {@code common}
 * không được phép phụ thuộc {@code features}. Để ở đây thì cả hai bên dùng chung một bộ
 * thuộc tính, không còn hai đoạn dựng cookie song song dễ lệch nhau.</p>
 *
 * <p>Toàn bộ thuộc tính đọc từ {@code app.auth.cookie.*} nên tự ăn theo môi trường:
 * Local {@code secure=false, SameSite=Lax} — Deploy {@code secure=true, SameSite=None}.
 * Tên cookie trùng với tên mà {@code JwtAuthenticationFilter} đang đọc.</p>
 */
@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    private final JwtProperties jwtProperties;
    private final AuthCookieProperties cookieProperties;

    public ResponseCookie accessTokenCookie(String token) {
        return build(cookieProperties.accessTokenName(), token, Duration.ofMillis(jwtProperties.getAccessTtlMs()));
    }

    public ResponseCookie refreshTokenCookie(String token) {
        return build(cookieProperties.refreshTokenName(), token, Duration.ofMillis(jwtProperties.getRefreshTtlMs()));
    }

    /** Ghi đè cookie rỗng với maxAge = 0 để trình duyệt xoá ngay. */
    public ResponseCookie clearAccessTokenCookie() {
        return build(cookieProperties.accessTokenName(), "", Duration.ZERO);
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return build(cookieProperties.refreshTokenName(), "", Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)          // JavaScript không đọc được ⇒ chống XSS đánh cắp token
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path())
                .maxAge(maxAge)
                .build();
    }
}
