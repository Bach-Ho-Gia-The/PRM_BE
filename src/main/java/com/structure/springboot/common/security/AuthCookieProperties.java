package com.structure.springboot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

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
    /**
     * <b>Dùng {@code hasText} chứ KHÔNG dùng {@code != null}.</b> Cú pháp {@code ${BIEN:mac-dinh}}
     * của Spring chỉ lấy giá trị mặc định khi biến môi trường <i>không tồn tại</i>; khai báo
     * {@code AUTH_COOKIE_PATH=} trong {@code .env} rồi bỏ trống là biến <i>có tồn tại</i> và
     * mang chuỗi rỗng, nên phép so null thả nó lọt qua.
     *
     * <p>Hậu quả rất khó lần ra vì không có lỗi nào được ném: {@code path} rỗng sinh cookie
     * không khớp đường dẫn nào nên trình duyệt im lặng không gửi lại — đúng kiểu "đăng nhập
     * thành công nhưng mọi request sau đó đều 401". {@code sameSite} rỗng sinh thuộc tính
     * {@code SameSite=} què. Riêng tên cookie rỗng thì {@code ResponseCookie.from("")} ném
     * thẳng, mỗi lần đăng nhập là một lỗi 500.</p>
     */
    public AuthCookieProperties {
        accessTokenName = StringUtils.hasText(accessTokenName) ? accessTokenName : "access_token";
        refreshTokenName = StringUtils.hasText(refreshTokenName) ? refreshTokenName : "refresh_token";
        sameSite = StringUtils.hasText(sameSite) ? sameSite : "Lax";
        path = StringUtils.hasText(path) ? path : "/";
    }
}
