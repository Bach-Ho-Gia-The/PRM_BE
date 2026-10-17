package com.structure.springboot.features.product.dto.request;

import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body của {@code POST /products/admin}.
 *
 * <p>{@code slug} để trống thì service tự sinh từ {@code name}. Tồn kho và gallery
 * là tài nguyên con, không nằm trong request này.</p>
 */
public record ProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,

        @Size(max = 255, message = "Slug must not exceed 255 characters")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug may contain only lowercase letters, digits and hyphens")
        String slug,

        @NotBlank(message = "Product SKU is required")
        @Size(max = 50, message = "SKU must not exceed 50 characters")
        String sku,

        @NotNull(message = "Price is required")
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

        @NotNull(message = "Category ID is required")
        UUID categoryId,

        // Null keeps the pre-existing ACTIVE default. Admin can create HIDDEN before uploading.
        RecordStatus status
) {
}
