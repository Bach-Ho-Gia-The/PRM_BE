package com.structure.springboot.features.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /auth/google}.
 *
 * <p>Chỉ nhận ID token. Mọi thông tin khác (email, tên, ảnh) đều được bóc ra từ chính token đã
 * ký — <b>không bao giờ lấy từ body</b>, nếu không client tự khai email nào cũng chiếm được
 * tài khoản đó.</p>
 */
public record GoogleLoginRequest(
        @NotBlank(message = "idToken is required")
        // Endpoint công khai, không qua xác thực. Không chặn độ dài thì ai cũng ném được vài MB
        // chuỗi rác vào đây và bắt NimbusJwtDecoder ngồi phân tích. ID token của Google quanh
        // 1-2 KB, 4096 là rộng rãi.
        @Size(max = 4096, message = "idToken is too long")
        String idToken
) {
}
