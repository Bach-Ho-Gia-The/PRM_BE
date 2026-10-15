package com.structure.springboot.features.product.dto.response;

import java.util.UUID;

/** Một ảnh trong gallery. Nằm trong {@link ProductDetailResponse#gallery()}. */
public record ProductImageResponse(
        UUID id,
        String imageUrl,
        Integer sortOrder
) {
}
