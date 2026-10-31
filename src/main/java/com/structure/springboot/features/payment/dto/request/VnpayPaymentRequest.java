package com.structure.springboot.features.payment.dto.request;

import java.math.BigDecimal;
import java.time.Instant;

public record VnpayPaymentRequest(
        String txnRef,
        BigDecimal amount,
        Instant createdAt,
        Instant expiresAt,
        String clientIp
) {
}