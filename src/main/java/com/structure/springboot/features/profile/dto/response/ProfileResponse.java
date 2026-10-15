package com.structure.springboot.features.profile.dto.response;

import com.structure.springboot.features.profile.enums.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hồ sơ trả về cho frontend — phẳng tuyệt đối.
 *
 * <p>{@code username} và {@code email} được kéo thẳng từ account (flatten quan hệ nhiều-1 y như
 * {@code categoryName} bên product), để màn "Tài khoản của tôi" chỉ cần gọi một API.</p>
 */
public record ProfileResponse(
        UUID id,
        UUID accountId,
        String username,
        String email,
        String fullName,
        String phone,
        LocalDate birthDate,
        Gender gender,
        String avatarUrl,
        String address,
        Instant createdAt,
        Instant updatedAt
) {
}
