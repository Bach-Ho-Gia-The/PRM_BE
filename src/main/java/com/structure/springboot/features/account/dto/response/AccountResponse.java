package com.structure.springboot.features.account.dto.response;

import com.structure.springboot.features.account.enums.AccountStatus;
import com.structure.springboot.features.account.enums.AuthProvider;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String email,
        UUID roleId,
        String roleName,
        AccountStatus status,
        AuthProvider provider,
        boolean hasPassword,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt
) {
}
