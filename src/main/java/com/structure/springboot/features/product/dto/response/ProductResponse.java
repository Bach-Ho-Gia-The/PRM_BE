package com.structure.springboot.features.product.dto.response;

import com.structure.springboot.features.product.enums.ProductCollection;
import com.structure.springboot.infrastructure.persistence.RecordStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sản phẩm ở dạng thẻ trong lưới danh sách — <b>phẳng tuyệt đối</b>, không kèm gallery hay variant.
 *
 * <p>Một trang lưới có 8–20 thẻ; nhét thêm 5 ảnh và 3 variant vào mỗi thẻ là tải về gấp mấy lần
 * dữ liệu mà màn hình không dùng tới. Chi tiết đầy đủ nằm ở {@link ProductDetailResponse}.</p>
 */
public record ProductResponse(
        UUID id,
        String name,
        String slug,
        String sku,
        BigDecimal price,
        BigDecimal originalPrice,
        String tag,
        String thumbnail,
        ProductCollection collection,
        Boolean isSummer,
        Boolean isFeatured,
        RecordStatus status,
        UUID categoryId,
        String categoryName,
        String categorySlug,
        Instant createdAt,
        Instant updatedAt
) {
}
