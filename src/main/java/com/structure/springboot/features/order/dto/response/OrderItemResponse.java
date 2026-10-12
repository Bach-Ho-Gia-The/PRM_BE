package com.structure.springboot.features.order.dto.response;

import com.structure.springboot.features.product.enums.ProductSize;

import java.math.BigDecimal;
import java.util.UUID;

/** Một dòng hàng đã chốt trong đơn — giá và tên là bản chụp tại thời điểm đặt. */
public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSlug,
        String imageUrl,
        ProductSize size,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal
) {
}
