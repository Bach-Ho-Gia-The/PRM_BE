package com.structure.springboot.infrastructure.external.vnpay;

import com.structure.springboot.features.payment.dto.request.VnpayPaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VnpayPaymentUrlBuilder {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayProperties properties;

    public String build(VnpayPaymentRequest request) {
        validate(request);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("vnp_Version", properties.version());
        parameters.put("vnp_Command", properties.command());
        parameters.put("vnp_TmnCode", properties.tmnCode());
        parameters.put("vnp_Amount", toGatewayAmount(request.amount()));
        parameters.put("vnp_CurrCode", properties.currency());
        parameters.put("vnp_TxnRef", request.txnRef());
        parameters.put(
                "vnp_OrderInfo",
                "Thanh toan " + request.txnRef()
        );
        parameters.put("vnp_OrderType", properties.orderType());
        parameters.put("vnp_Locale", properties.locale());
        parameters.put("vnp_ReturnUrl", properties.returnUrl());
        parameters.put("vnp_IpAddr", request.clientIp());
        parameters.put(
                "vnp_CreateDate",
                DATE_FORMAT.withZone(properties.zoneId())
                        .format(request.createdAt())
        );
        parameters.put(
                "vnp_ExpireDate",
                DATE_FORMAT.withZone(properties.zoneId())
                        .format(request.expiresAt())
        );

        String query = VnpaySignature.canonicalQuery(parameters);
        String signature = VnpaySignature.sign(
                query, properties.hashSecret());

        return properties.paymentUrl()
                + "?" + query
                + "&vnp_SecureHash=" + signature;
    }

    private void validate(VnpayPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment request is required");
        }

        if (request.txnRef() == null
                || !request.txnRef().matches("[a-zA-Z0-9]{1,100}")) {
            throw new IllegalArgumentException(
                    "Invalid payment transaction reference");
        }

        if (request.createdAt() == null
                || request.expiresAt() == null
                || !request.expiresAt().isAfter(request.createdAt())) {
            throw new IllegalArgumentException(
                    "Invalid payment timestamps");
        }

        if (request.clientIp() == null || request.clientIp().isBlank()) {
            throw new IllegalArgumentException("Client IP is required");
        }
    }

    private String toGatewayAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive");
        }

        String value;
        try {
            value = amount.movePointRight(2)
                    .toBigIntegerExact()
                    .toString();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Payment amount must have at most two decimal places",
                    exception);
        }

        if (value.length() > 12) {
            throw new IllegalArgumentException(
                    "Payment amount exceeds VNPay field length");
        }

        return value;
    }
}
