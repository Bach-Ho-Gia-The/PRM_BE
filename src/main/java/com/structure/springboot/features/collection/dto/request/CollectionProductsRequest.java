package com.structure.springboot.features.collection.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

/** Version lấy từ GET/POST response; danh sách ID đầy đủ theo thứ tự, chỉ lưu khi BST HIDDEN. */
public record CollectionProductsRequest(
        @NotNull(message = "Expected version is required")
        @PositiveOrZero(message = "Expected version must be zero or greater")
        Long expectedVersion,

        @NotEmpty(message = "Collection must contain at least one product")
        List<@NotNull(message = "Product ID must not be null") UUID> productIds
) {
}
