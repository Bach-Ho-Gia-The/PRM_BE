package com.structure.springboot.features.auth.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Body của {@code POST /auth/verify-otp} — bước 2: đổi mã 6 số lấy vé tạm. */
public record VerifyOtpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String email,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String otp
) {
}
