package com.structure.springboot.features.category.dto.response;

import com.structure.springboot.infrastructure.persistence.RecordStatus;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String categoryName,
        String slug,
        RecordStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
