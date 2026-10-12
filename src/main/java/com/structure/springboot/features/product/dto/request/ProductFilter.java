package com.structure.springboot.features.product.dto.request;

import com.structure.springboot.features.product.enums.ProductCollection;

import java.math.BigDecimal;

/**
 * Gom các query param lọc sản phẩm của {@code GET /products}.
 *
 * <p>Để một record thay vì bảy tham số {@code @RequestParam} rời: chữ ký controller và chữ ký
 * service đều ngắn lại, và thêm bộ lọc mới sau này chỉ phải sửa đúng một chỗ. Spring tự bind
 * query param vào record qua constructor nên không cần annotation gì thêm.</p>
 *
 * <p>Mọi field đều có thể null = "không lọc theo tiêu chí này".</p>
 */
public record ProductFilter(
        String category,
        ProductCollection collection,
        String search,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean featured
) {
}
