package com.structure.springboot.features.collection.dto.response;

import com.structure.springboot.infrastructure.persistence.RecordStatus;

import java.time.Instant;
import java.util.UUID;

/** Dòng trong danh sách quản trị: không nạp các sản phẩm liên kết. */
public record CollectionSummaryResponse(
        UUID id,
        String collectionName,
        String slug,
        String coverImageUrl,
        RecordStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
