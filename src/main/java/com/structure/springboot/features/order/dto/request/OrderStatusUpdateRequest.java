package com.structure.springboot.features.order.dto.request;

import com.structure.springboot.features.order.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Admin chuyển trạng thái đơn.
 *
 * <p>Chỉ một trường: mọi thứ khác của đơn đã chốt lúc đặt hàng, và chuyển trạng thái là hành
 * động riêng chứ không phải sửa đơn.</p>
 */
public record OrderStatusUpdateRequest(

        @NotNull(message = "Status is required")
        OrderStatus status
) {
}
