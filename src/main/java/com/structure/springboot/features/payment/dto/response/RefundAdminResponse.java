package com.structure.springboot.features.payment.dto.response;

import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng trong danh sách hoàn tiền của shop.
 *
 * <p>Record phẳng, không lồng — bám golden standard như {@link PaymentResponse}. Thông tin đơn đã
 * được trải phẳng thành {@code orderNumber} và {@code customerName} nên phía đọc không phải biết tới
 * khoá ngoại hay hình dạng projection nội bộ.</p>
 *
 * @param id           mã khoản hoàn
 * @param orderNumber  số đơn khách đọc trên hoá đơn
 * @param customerName tên người nhận chụp lại trên đơn
 * @param amount       số tiền phải hoàn (lấy từ {@code payments.amount})
 * @param reason       lý do phát sinh — phân biệt "huỷ đơn đã trả tiền" với "tiền về sau khi huỷ"
 * @param status       trạng thái khoản hoàn
 * @param createdAt    thời điểm phát sinh
 */
public record RefundAdminResponse(
        UUID id,
        String orderNumber,
        String customerName,
        BigDecimal amount,
        RefundReason reason,
        RefundStatus status,
        Instant createdAt
) {
}
