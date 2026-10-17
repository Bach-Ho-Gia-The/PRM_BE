package com.structure.springboot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình đăng nhập Google, đọc từ {@code app.auth.google.*}.
 *
 * <p>Chỉ có {@code clientId} vì dự án đi theo luồng "frontend lấy ID token rồi gửi về backend":
 * backend không bao giờ gọi tới endpoint token của Google nên không cần client secret. Ít một bí
 * mật phải bảo quản là bớt một chỗ rò rỉ.</p>
 *
 * <p>Giá trị dùng để kiểm claim {@code aud} — bỏ bước đó thì ID token do BẤT KỲ ứng dụng Google
 * nào phát hành cũng đăng nhập được vào hệ thống này.</p>
 */
@ConfigurationProperties(prefix = "app.auth.google")
public record GoogleAuthProperties(
        String clientId
) {
}
