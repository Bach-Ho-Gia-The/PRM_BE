package com.structure.springboot.features.auth.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Body của {@code POST /auth/forgot-password} — bước 1: xin mã xác minh. */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String email
) {
}
