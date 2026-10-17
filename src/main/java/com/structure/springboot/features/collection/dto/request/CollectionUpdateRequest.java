package com.structure.springboot.features.collection.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * PATCH metadata khi BST đang HIDDEN. expectedVersion lấy từ response đọc BST; các field
 * metadata vắng/null giữ nguyên, clearCoverImage=true xoá URL ảnh bìa (và file managed).
 * Trạng thái và sản phẩm có thao tác riêng để không bỏ qua các quy tắc đăng/xoá BST.
 */
public record CollectionUpdateRequest(
        @NotNull(message = "Expected version is required")
        @PositiveOrZero(message = "Expected version must be zero or greater")
        Long expectedVersion,

        @Size(max = 100, message = "Collection name must not exceed 100 characters")
        String collectionName,

        @Size(max = 100, message = "Slug must not exceed 100 characters")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug may contain only lowercase letters, digits and hyphens")
        String slug,

        @Size(max = 500, message = "Cover URL must not exceed 500 characters")
        String coverImageUrl,

        Boolean clearCoverImage
) {
}
