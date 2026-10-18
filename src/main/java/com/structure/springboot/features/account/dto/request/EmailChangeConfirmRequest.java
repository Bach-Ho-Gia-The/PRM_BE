package com.structure.springboot.features.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Bước 2 của luồng đổi email: mã 6 số vừa gửi tới địa chỉ mới. */
public record EmailChangeConfirmRequest(
        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
        String otp
) {
}
