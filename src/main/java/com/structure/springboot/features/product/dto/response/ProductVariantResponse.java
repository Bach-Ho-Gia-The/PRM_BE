package com.structure.springboot.features.product.dto.response;

import com.structure.springboot.features.product.enums.ProductSize;

import java.util.UUID;

/** Tồn kho của một size. Nằm trong {@link ProductDetailResponse#variants()}. */
public record ProductVariantResponse(
        UUID id,
        ProductSize size,
        Integer stockQuantity,
        /** Gửi lại ở {@code expectedVersion} khi sửa tồn kho. */
        Long version
) {
}
