package com.structure.springboot.features.payment.dto.response;

import com.structure.springboot.features.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Trạng thái thanh toán của một đơn, hiển thị cho <b>cả khách và shop</b>.
 *
 * <p>Một record phẳng, không lồng — bám golden standard. Quan hệ nhiều-một đã trải phẳng thành
 * {@code orderNumber} nên phía đọc không phải biết tới khoá ngoại.</p>
 *
 * <p>Các trường đặc thù VNPay ({@code bankCode}, {@code paidAt}) để {@code null} với đơn COD —
 * chúng chỉ có giá trị khi tiền đã thực sự chạy qua cổng.</p>
 */
public record PaymentResponse(
        UUID id,
        String orderNumber,
        String txnRef,
        Integer attemptNo,
        PaymentStatus status,
        BigDecimal amount,
        /** {@code null} với COD: đơn trả tiền khi nhận hàng thì không có hạn. */
        Instant expiresAt,
        Instant paidAt,
        String bankCode,
        Instant createdAt,
        Instant updatedAt
) {
}
