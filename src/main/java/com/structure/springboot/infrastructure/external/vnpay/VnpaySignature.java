package com.structure.springboot.infrastructure.external.vnpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class VnpaySignature {

    private VnpaySignature() {
    }

    /**
     * Chỉ nhận tham số VNPay chưa URL-encode.
     * Dùng cùng chuỗi này để ký và tạo query thanh toán.
     */
    public static String canonicalQuery(Map<String, String> parameters) {
        return new TreeMap<>(parameters).entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("vnp_"))
                .filter(entry -> !entry.getKey().equals("vnp_SecureHash"))
                .filter(entry -> !entry.getKey().equals("vnp_SecureHashType"))
                .filter(entry -> entry.getValue() != null
                        && !entry.getValue().isEmpty())
                .map(entry -> encode(entry.getKey())
                        + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String sign(String canonicalQuery, String hashSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    hashSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512"
            ));

            byte[] signature = mac.doFinal(
                    canonicalQuery.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(signature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Cannot calculate VNPay signature", exception);
        }
    }

    public static boolean verify(
            Map<String, String> parameters,
            String hashSecret
    ) {
        String receivedHash = parameters.get("vnp_SecureHash");

        if (receivedHash == null || receivedHash.length() != 128) {
            return false;
        }

        byte[] receivedBytes;
        try {
            receivedBytes = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        String expectedHash = sign(canonicalQuery(parameters), hashSecret);
        byte[] expectedBytes = HexFormat.of().parseHex(expectedHash);

        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}