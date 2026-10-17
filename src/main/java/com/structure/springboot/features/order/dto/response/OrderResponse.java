package com.structure.springboot.features.order.dto.response;

import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Đơn hàng đầy đủ.
 *
 * <p>{@code items} lồng theo phương án A đã duyệt — một đơn hàng không có danh sách món thì
 * không còn là đơn hàng. Quan hệ nhiều-một ({@code account}) vẫn trải phẳng thành
 * {@code accountId} như golden standard.</p>
 */
public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID accountId,
        OrderStatus status,
        PaymentMethod paymentMethod,
        String receiverName,
        String phone,
        String email,
        String address,
        String note,
        /** Chỉ có giá trị khi khách tự huỷ; đơn do admin huỷ thì null. */
        String cancelReason,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal discount,
        BigDecimal total,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
