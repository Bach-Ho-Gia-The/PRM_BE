package com.structure.springboot.features.auth.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Body của {@code POST /auth/verify-registration} — bước 2 của đăng ký.
 *
 * <p><b>Nhận {@code registrationId} chứ không nhận email:</b> mã phiên này do bước 1 cấp và gắn
 * chặt với đúng bộ dữ liệu đã khai. Nếu định danh bằng email thì hai người cùng đăng ký một địa
 * chỉ sẽ dùng chung một hồ sơ, và mã gửi tới hộp thư có thể hoàn tất hồ sơ của người kia.</p>
 */
public record VerifyRegistrationRequest(
        @NotBlank(message = "registrationId is required")
        // Cùng lý do với ticket ở ResetPasswordRequest: giá trị này thành khoá Redis.
        @Size(max = 64, message = "registrationId is not valid")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String registrationId,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String otp
) {
}
