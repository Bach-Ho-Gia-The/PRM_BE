package com.structure.springboot.features.product.dto.response;

import com.structure.springboot.features.product.enums.ProductCollection;
import com.structure.springboot.infrastructure.persistence.RecordStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sản phẩm đầy đủ cho trang chi tiết.
 *
 * <p><b>Ngoại lệ có chủ đích với quy ước "DTO phẳng, không nested"</b> (đã được duyệt): hai quan hệ
 * một-nhiều {@code gallery} và {@code variants} buộc phải lồng. Trải phẳng thành {@code img1..img5}
 * hay {@code stockS/stockM/stockL} sẽ khoá cứng số lượng ảnh và danh sách size vào DTO — thêm một
 * size mới là phải sửa cả DTO, mapper lẫn frontend.</p>
 *
 * <p>Quan hệ nhiều-một ({@code category}) vẫn trải phẳng như golden standard.</p>
 */
public record ProductDetailResponse(
        UUID id,
        String name,
        String slug,
        String sku,
        BigDecimal price,
        BigDecimal originalPrice,
        String description,
        String material,
        String careInstructions,
        String tag,
        String thumbnail,
        ProductCollection collection,
        Boolean isSummer,
        Boolean isFeatured,
        RecordStatus status,
        UUID categoryId,
        String categoryName,
        String categorySlug,
        List<ProductImageResponse> gallery,
        List<ProductVariantResponse> variants,
        Instant createdAt,
        Instant updatedAt
) {
}
