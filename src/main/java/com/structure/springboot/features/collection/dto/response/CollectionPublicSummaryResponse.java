package com.structure.springboot.features.collection.dto.response;

import java.util.UUID;

/** Thẻ BST phía cửa hàng: không trả version/status hay các liên kết quản trị. */
public record CollectionPublicSummaryResponse(
        UUID id,
        String collectionName,
        String slug,
        String coverImageUrl
) {
}
