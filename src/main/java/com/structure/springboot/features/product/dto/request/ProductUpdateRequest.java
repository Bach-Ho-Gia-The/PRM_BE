package com.structure.springboot.features.product.dto.request;

import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body của {@code PATCH /products/admin/{id}} — cập nhật MỘT PHẦN.
 * Field nào không gửi (hoặc gửi null) thì giữ nguyên giá trị cũ trong DB.
 *
 * <p>Gửi {@code status} là cách admin ẩn/hiện lại sản phẩm. Gửi {@code DELETED} cũng được nhưng
 * nên dùng {@code DELETE /products/admin/{id}} cho đúng ngữ nghĩa HTTP.</p>
 */
public record ProductUpdateRequest(
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,

        @Size(max = 255, message = "Slug must not exceed 255 characters")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug may contain only lowercase letters, digits and hyphens")
        String slug,

        @Size(max = 50, message = "SKU must not exceed 50 characters")
        String sku,

        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        BigDecimal price,

        @DecimalMin(value = "0.0", inclusive = false, message = "Original price must be greater than 0")
        BigDecimal originalPrice,

        String description,

        String material,

        String careInstructions,

        @Size(max = 50, message = "Tag must not exceed 50 characters")
        String tag,

        @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
        String thumbnail,

        Boolean isSummer,

        Boolean isFeatured,

        RecordStatus status,

        UUID categoryId
) {
}
