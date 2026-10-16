package com.structure.springboot.infrastructure.initializer;

import com.structure.springboot.features.product.enums.ProductSize;

import java.math.BigDecimal;
import java.util.List;

/**
 * Khuôn đọc file {@code resources/data/products.json} cho {@link DataInitializer}.
 *
 * <p>Để dữ liệu mẫu ở JSON thay vì gõ 18 sản phẩm bằng Java: phần mô tả, chất liệu, gallery chiếm
 * vài trăm dòng chuỗi thuần, nhét vào mã nguồn chỉ làm loãng file mà không thêm chút logic nào.</p>
 *
 * <p>Đây KHÔNG phải DTO của tầng web — nó không đi ra ngoài qua API, nên được phép lồng nhau.</p>
 */
public record ProductSeedData(
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
        Boolean isSummer,
        Boolean isFeatured,
        String categorySlug,
        List<String> gallery,
        List<Variant> variants
) {

    public record Variant(ProductSize size, Integer stockQuantity) {
    }
}
