package com.structure.springboot.features.account.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String username,
        String email,
        String roleName,
        Instant createdAt,
        Instant updatedAt
) {
}
