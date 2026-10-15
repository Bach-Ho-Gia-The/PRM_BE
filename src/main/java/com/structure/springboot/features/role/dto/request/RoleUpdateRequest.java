package com.structure.springboot.features.role.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Body của {@code PATCH /roles/{id}} — cập nhật MỘT PHẦN.
 * Field nào không gửi (hoặc gửi null) thì giữ nguyên giá trị cũ trong DB.
 */
public record RoleUpdateRequest(
        @Size(max = 50, message = "Role name must not exceed 50 characters")
        @JsonDeserialize(using = JacksonConfig.UpperCaseTrimDeserializer.class)
        String roleName,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description
) {}
