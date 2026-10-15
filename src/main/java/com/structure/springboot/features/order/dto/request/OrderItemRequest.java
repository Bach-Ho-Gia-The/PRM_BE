package com.structure.springboot.features.order.dto.request;

import com.structure.springboot.features.product.enums.ProductSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Một dòng hàng client gửi lên khi đặt đơn.
 *
 * <p>CHỈ nhận {@code productId} + {@code size} + {@code quantity}. Giá và tên sản phẩm do backend
 * tự tra từ DB — nhận giá do client gửi lên thì ai cũng mua được váy 720.000₫ với giá 1.000₫.</p>
 */
public record OrderItemRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Size is required")
        ProductSize size,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 999, message = "Quantity must not exceed 999")
        Integer quantity
) {
}
