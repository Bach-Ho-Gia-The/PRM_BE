package com.structure.springboot.features.category.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {
}
