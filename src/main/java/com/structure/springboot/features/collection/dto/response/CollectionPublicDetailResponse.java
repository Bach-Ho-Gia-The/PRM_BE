package com.structure.springboot.features.collection.dto.response;

import com.structure.springboot.common.response.PageResponse;

import java.util.UUID;

/** Chi tiết cửa hàng: metadata BST và đúng một trang Product đang hiển thị. */
public record CollectionPublicDetailResponse(
        UUID id,
        String collectionName,
        String slug,
        String coverImageUrl,
        PageResponse<CollectionPublicProductResponse> products
) {
}
