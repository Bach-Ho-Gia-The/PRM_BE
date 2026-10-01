package com.structure.springboot.features.role.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(max = 50, message = "Role name must not exceed 50 characters")
        @JsonDeserialize(using = JacksonConfig.UpperCaseTrimDeserializer.class)
        String roleName,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description
) {}
