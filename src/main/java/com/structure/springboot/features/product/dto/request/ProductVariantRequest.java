package com.structure.springboot.features.product.dto.request;

import com.structure.springboot.features.product.enums.ProductSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Thêm một size vào sản phẩm. Size là bất biến: muốn đổi thì xoá rồi tạo lại. */
public record ProductVariantRequest(
        @NotNull(message = "Size is required")
        ProductSize size,

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity must be zero or greater")
        @Max(value = 100000, message = "Stock quantity must not exceed 100000")
        Integer stockQuantity
) {
}
