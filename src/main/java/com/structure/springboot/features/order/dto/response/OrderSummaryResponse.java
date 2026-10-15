package com.structure.springboot.features.order.dto.response;

import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Đơn hàng ở dạng dòng trong danh sách "Đơn của tôi" — <b>phẳng tuyệt đối, không kèm items</b>.
 *
 * <p>Màn hình danh sách chỉ hiện mã đơn, trạng thái và tổng tiền. Kéo theo toàn bộ dòng hàng của
 * mọi đơn là vừa tốn băng thông vừa đẻ N+1; bấm vào một đơn mới gọi {@link OrderResponse}.</p>
 */
public record OrderSummaryResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        PaymentMethod paymentMethod,
        String receiverName,
        BigDecimal total,
        Integer totalItems,
        Instant createdAt
) {
}
