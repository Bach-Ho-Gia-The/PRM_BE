package com.structure.springboot.features.product.dto.request;

import java.math.BigDecimal;

/**
 * Các query param tuỳ chọn của GET /products. Category và Collection nhận slug thật;
 * collection không còn là enum SUNSET/REGULAR của Product.
 */
public record ProductFilter(
        String category,
        String collection,
        String search,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean featured
) {
}
