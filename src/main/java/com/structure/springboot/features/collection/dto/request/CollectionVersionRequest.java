package com.structure.springboot.features.collection.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Body cho thao tác publish/hide: chỉ thực hiện nếu BST vẫn ở version client đã đọc. */
public record CollectionVersionRequest(
        @NotNull(message = "Expected version is required")
        @PositiveOrZero(message = "Expected version must be zero or greater")
        Long expectedVersion
) {
}
