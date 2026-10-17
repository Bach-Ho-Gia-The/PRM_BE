package com.structure.springboot.features.store.dto.response;

import com.structure.springboot.infrastructure.persistence.RecordStatus;

import java.time.Instant;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        String storeName,
        String address,
        String phone,
        String openingHours,
        Double latitude,
        Double longitude,
        String imageUrl,
        RecordStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
