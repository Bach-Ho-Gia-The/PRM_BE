package com.structure.springboot.features.role.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(max = 50, message = "Role name must not exceed 50 characters")
        String roleName,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description
) {
    public RoleRequest {
        roleName = (roleName != null && !roleName.isBlank())
                ? roleName.trim().toUpperCase()
                : null;
    }
}
