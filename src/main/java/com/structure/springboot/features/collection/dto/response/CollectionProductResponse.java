package com.structure.springboot.features.collection.dto.response;

import com.structure.springboot.infrastructure.persistence.RecordStatus;

import java.util.UUID;

/** Admin thấy cả sản phẩm đã ẩn/xoá để dọn liên kết; khách chỉ thấy bản lọc ở task 2C. */
public record CollectionProductResponse(
        UUID productId,
        String productName,
        String productSlug,
        String thumbnail,
        RecordStatus status,
        Integer sortOrder
) {
}
