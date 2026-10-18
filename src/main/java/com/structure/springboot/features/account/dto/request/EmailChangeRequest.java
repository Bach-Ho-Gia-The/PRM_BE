package com.structure.springboot.features.account.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Bước 1 của luồng đổi email: xin mã xác minh gửi tới địa chỉ MỚI.
 *
 * <p>Mật khẩu hiện tại chứng minh "đúng người đang ngồi đây"; mã OTP ở bước 2 chứng minh "đúng
 * hộp thư này là của bạn". Thiếu vế thứ hai thì ai gõ nhầm một chữ là mất tài khoản, và ai mượn
 * được máy đang đăng nhập cũng dời được địa chỉ khôi phục đi chỗ khác.</p>
 */
public record EmailChangeRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String newEmail
) {
}
