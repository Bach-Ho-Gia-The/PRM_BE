package com.structure.springboot.features.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Sửa tồn kho của một size.
 *
 * <p>{@code expectedVersion} bắt buộc: hai admin cùng nhập hàng về, một người gõ 50 một người gõ 30,
 * không có version thì kho thành 30 và mất 50 cái mà không ai biết.</p>
 */
public record ProductVariantUpdateRequest(
        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity must be zero or greater")
        @Max(value = 100000, message = "Stock quantity must not exceed 100000")
        Integer stockQuantity,

        @NotNull(message = "Expected version is required")
        @PositiveOrZero(message = "Expected version must be zero or greater")
        Long expectedVersion
) {
}
