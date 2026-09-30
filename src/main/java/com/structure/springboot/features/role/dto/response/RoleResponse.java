package com.structure.springboot.features.role.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String roleName,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
