package com.structure.springboot.features.collection.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Thẻ sản phẩm trong BST công khai; không kèm trạng thái, tồn kho hay gallery. */
public record CollectionPublicProductResponse(
        UUID productId,
        String productName,
        String productSlug,
        String thumbnail,
        BigDecimal price,
        BigDecimal originalPrice,
        String tag
) {
}
