package com.structure.springboot.features.collection.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Tạo BST ẩn kèm ít nhất một sản phẩm; không có giai đoạn lưu BST rỗng. */
public record CollectionCreateRequest(
        @NotBlank(message = "Collection name is required")
        @Size(max = 100, message = "Collection name must not exceed 100 characters")
        String collectionName,

        @Size(max = 100, message = "Slug must not exceed 100 characters")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug may contain only lowercase letters, digits and hyphens")
        String slug,

        @Size(max = 500, message = "Cover URL must not exceed 500 characters")
        String coverImageUrl,

        @NotEmpty(message = "Collection must contain at least one product")
        List<@NotNull(message = "Product ID must not be null") UUID> productIds
) {
}
