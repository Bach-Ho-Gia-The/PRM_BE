package com.structure.springboot.features.auth.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Đăng nhập bằng <b>email</b>.
 *
 * <p>Email là danh tính đăng nhập duy nhất — {@code accounts} không còn cột nào khác đóng vai
 * định danh. Chọn email vì nó đã xuyên suốt hệ thống: claim {@code sub}
 * của token là email, mốc thu hồi phiên trong Redis cũng cắm theo email.</p>
 *
 * <p>Giới hạn 100 ký tự để khớp đúng độ dài cột {@code accounts.email}; để 50 như trước thì
 * email dài hơn sẽ bị chặn ngay ở validation, chưa kịp hỏi database.</p>
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 100, message = "Password must not exceed 100 characters")
        String password
) {
}
