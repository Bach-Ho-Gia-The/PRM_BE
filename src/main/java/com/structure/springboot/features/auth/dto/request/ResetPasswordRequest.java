package com.structure.springboot.features.auth.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Body của {@code POST /auth/reset-password} — bước 3: dùng vé tạm để đặt mật khẩu mới.
 *
 * <p>Không nhận lại {@code email}: vé đã gắn sẵn chủ sở hữu từ bước xác minh. Cho gửi kèm email
 * là mở đường đổi mật khẩu của người khác bằng vé của mình.</p>
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Ticket is required")
        // Giá trị này được ghép thẳng vào khoá Redis nên phải có trần, bằng không một chuỗi dài
        // tuỳ ý sẽ thành một khoá dài tuỳ ý. Backend luôn sinh UUID (36 ký tự).
        @Size(max = 64, message = "Ticket is not valid")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String ticket,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter and one digit")
        String newPassword
) {
}
