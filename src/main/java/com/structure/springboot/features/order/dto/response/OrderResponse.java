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
        /**
         * Hạn thanh toán của đơn. {@code null} với COD (trả tiền khi nhận hàng).
         *
         * <p>Đi kèm {@link #paidAt} để client tự dựng đúng trạng thái hiển thị mà <b>không cần một
         * trường enum trạng thái thanh toán riêng</b>:</p>
         * <ul>
         *   <li>COD + {@code paidAt == null} ⇒ "Thanh toán khi nhận hàng"</li>
         *   <li>BANK_TRANSFER + {@code paidAt == null} + còn hạn ⇒ "Chờ thanh toán" (đếm ngược được)</li>
         *   <li>BANK_TRANSFER + {@code paidAt == null} + quá hạn ⇒ "Đã hết hạn thanh toán"</li>
         *   <li>{@code paidAt != null} ⇒ "Đã thanh toán lúc …"</li>
         * </ul>
         * <p>Hiển thị <b>dữ kiện</b> (thời điểm) thay vì <b>nhãn</b>: nhãn phải đồng bộ mỗi khi
         * IPN về muộn hay tác vụ hết hạn chạy, còn thời điểm là sự thật không cần đồng bộ.</p>
         */
        Instant paymentDueAt,
        /**
         * Thời điểm đơn đã được trả tiền, {@code null} nếu chưa.
         *
         * <p>Khác {@code paymentMethod} ở chỗ đây là <b>sự kiện đã xảy ra</b>, không phải cách khách
         * chọn trả. Không có trường này thì đơn "PENDING chờ shop xác nhận" và đơn "PENDING đã trả
         * tiền chờ shop xác nhận" trông giống hệt nhau.</p>
         */
        Instant paidAt,
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
