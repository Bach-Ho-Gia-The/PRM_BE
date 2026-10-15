package com.structure.springboot.features.account.dto.response;

import com.structure.springboot.features.account.enums.AccountStatus;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String email,
        UUID roleId,
        String roleName,
        AccountStatus status,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt
) {
}
