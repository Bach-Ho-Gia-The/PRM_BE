package com.structure.springboot.features.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PATCH /accounts/me/password}.
 *
 * <p>Bắt nhập lại mật khẩu hiện tại là để kẻ mượn được phiên đăng nhập (máy quên khoá, token bị
 * đánh cắp) không thể chiếm luôn tài khoản bằng cách đổi mật khẩu.</p>
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter and one digit")
        String newPassword
) {
}
