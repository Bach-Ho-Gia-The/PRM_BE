package com.structure.springboot.features.payment.service;

import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.payment.dto.response.PaymentResponse;
import com.structure.springboot.features.payment.dto.response.VnpayPaymentUrlResponse;
import com.structure.springboot.features.payment.entity.PaymentEntity;

import java.util.UUID;

public interface PaymentService {

    /**
     * Ghi nhận <b>lần thử thanh toán đầu tiên</b> cho một đơn vừa được tạo. Gọi trong chính giao dịch
     * của việc tạo đơn, nên đơn và payment luôn ra đời cùng nhau hoặc cùng không ra đời.
     *
     * <p>Áp cho <b>cả COD</b>: đơn COD vẫn có bản ghi payment để shop bấm "đã thu tiền" sau này;
     * chỉ khác là {@code expiresAt = null} vì không có hạn thanh toán.</p>
     *
     * @param order đơn <b>đã được lưu</b> (cần có id và số tiền)
     * @return bản ghi payment vừa tạo
     */
    PaymentEntity createFirstAttempt(OrderEntity order);

    /**
     * Mở link thanh toán VNPay cho một đơn của chính khách đang đăng nhập.
     *
     * <p>Không phải lần gọi nào cũng sinh bản ghi mới — xem quy tắc ở phần cài đặt. Điểm quan trọng
     * nhất: <b>mở lại link không bao giờ gia hạn thêm thời gian</b>.</p>
     *
     * @param clientIp IP của khách, dùng cho tham số {@code vnp_IpAddr}
     */
    VnpayPaymentUrlResponse startVnpayPayment(UUID accountId, String orderNumber, String clientIp);

    /** Trạng thái thanh toán của một đơn của chính khách đang đăng nhập. Chỉ đọc. */
    PaymentResponse getMyPayment(UUID accountId, String orderNumber);
}
