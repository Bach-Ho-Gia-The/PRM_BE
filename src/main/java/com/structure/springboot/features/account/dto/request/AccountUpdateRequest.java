package com.structure.springboot.features.account.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.UUID;

/**
 * Body của {@code PATCH /accounts/{id}} — cập nhật MỘT PHẦN.
 *
 * <p>Khác {@link AccountRequest} ở chỗ mọi field đều <b>không bắt buộc</b>: field nào không gửi
 * (hoặc gửi null) thì giữ nguyên giá trị cũ trong DB. Vì vậy ở đây không có {@code @NotBlank}
 * hay {@code @NotNull} — chỉ còn ràng buộc về định dạng, và các ràng buộc đó chỉ chạy khi
 * field thực sự có giá trị.</p>
 *
 * <p><b>KHÔNG có field {@code password}</b>: đổi mật khẩu phải đi qua
 * {@code PATCH /accounts/me/password} và nhập lại mật khẩu hiện tại. Để chung ở đây thì bất kỳ ai
 * gọi được API sửa tài khoản là chiếm luôn được tài khoản đó.</p>
 */
public record AccountUpdateRequest(
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String email,

        UUID roleId
) {}
