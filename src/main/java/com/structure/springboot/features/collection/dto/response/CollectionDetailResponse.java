package com.structure.springboot.features.collection.dto.response;

import com.structure.springboot.infrastructure.persistence.RecordStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chi tiết quản trị, gồm đủ liên kết và số sản phẩm có thể hiển thị để cảnh báo khi bằng 0. */
public record CollectionDetailResponse(
        UUID id,
        String collectionName,
        String slug,
        String coverImageUrl,
        RecordStatus status,
        Long version,
        List<CollectionProductResponse> products,
        long visibleProductCount,
        Instant createdAt,
        Instant updatedAt
) {
}
