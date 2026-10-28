package com.structure.springboot.features.payment.dto.response;

import com.structure.springboot.features.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kết quả của một lần mở link thanh toán VNPay.
 *
 * <p>Trả cả {@code txnRef} và {@code expiresAt} để client biết <b>mình đang ở lần thử thứ mấy và
 * còn bao nhiêu thời gian</b> — mở lại link không làm hạn dài thêm, nên {@code expiresAt} ở đây
 * luôn là hạn cũ.</p>
 */
public record VnpayPaymentUrlResponse(
        String paymentUrl,
        String txnRef,
        BigDecimal amount,
        PaymentStatus status,
        Instant expiresAt
) {
}
