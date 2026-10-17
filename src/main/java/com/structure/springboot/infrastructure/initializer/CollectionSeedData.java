package com.structure.springboot.infrastructure.initializer;

import java.util.List;

/** BST mẫu chỉ dùng khi DataInit bật; không phải enum hay thuộc tính lưu trên Product. */
public record CollectionSeedData(
        String collectionName,
        String slug,
        String coverImageUrl,
        List<String> productSkus
) {
}
