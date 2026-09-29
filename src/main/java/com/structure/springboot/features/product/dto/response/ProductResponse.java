package com.structure.springboot.features.product.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String sku,
        BigDecimal price,
        Integer stockQuantity,
        String description,
        UUID categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {
}
