package com.structure.springboot.infrastructure.external.vnpay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.ZoneId;

/**
 * Cấu hình cổng VNPay, gom từ {@code app.payment.vnpay.*}.
 *
 * <p><b>Kiểm tra URL nằm ở đây, không nằm trong {@code VnpayPaymentUrlBuilder}:</b> đây là cấu
 * hình của merchant — sai là sai ngay từ đầu, không phải chuyện của từng giao dịch. Compact
 * constructor chạy trong lúc Spring bind properties, tức là <b>lúc khởi động</b>; cấu hình sai thì
 * ứng dụng <b>không start</b>. Trước đây việc kiểm tra nằm trong builder nên cấu hình sai chỉ nổ
 * đúng lúc khách bấm thanh toán — muộn hơn nhiều so với lúc phát hiện được. Cùng khuôn với
 * {@code CloudinaryProperties}: sai cấu hình thì fail-fast.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.payment.vnpay")
public record VnpayProperties(
        @NotBlank String tmnCode,
        @NotBlank String hashSecret,
        @NotBlank String paymentUrl,
        @NotBlank String apiUrl,
        @NotBlank String returnUrl,
        @NotBlank String version,
        @NotBlank String command,
        @NotBlank String currency,
        @NotBlank String locale,
        @NotBlank String orderType,
        @NotNull ZoneId zoneId
) {

    public VnpayProperties {
        // Cổng thanh toán là địa chỉ cố định của VNPay, gọi kèm query do chính mình dựng ⇒
        // không được phép mang sẵn query/fragment, và bắt buộc HTTPS.
        validateUrl(paymentUrl, "payment URL", false);
        // Return URL thì ngược lại: nó vốn là một URL đầy đủ có thể kèm query riêng, và khi
        // chạy local buộc phải cho phép http (xem .env-example).
        validateUrl(returnUrl, "return URL", true);
    }

    /**
     * Kiểm một URL cấu hình.
     *
     * @param allowQuery {@code true} cho Return URL (được phép có query riêng),
     *                   {@code false} cho cổng thanh toán (phải là URL trần, bắt buộc HTTPS)
     */
    private static void validateUrl(
            String value,
            String field,
            boolean allowQuery
    ) {
        // @NotBlank đã chặn null/rỗng trước khi tới đây, nhưng compact constructor cũng chạy
        // trong unit test dựng record trực tiếp nên vẫn phải tự bảo vệ.
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "VNPay " + field + " must not be blank");
        }

        // URI.create ném IllegalArgumentException với thông điệp khô khan; bọc lại để người
        // cấu hình đọc là biết ngay mình sai ở khoá nào.
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "VNPay " + field + " is not a valid URL", exception);
        }

        boolean http = "http".equalsIgnoreCase(uri.getScheme());
        boolean https = "https".equalsIgnoreCase(uri.getScheme());

        if ((!http && !https)
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || (!allowQuery && uri.getRawQuery() != null)) {
            throw new IllegalArgumentException("Invalid VNPay " + field);
        }

        if (!allowQuery && !https) {
            throw new IllegalArgumentException(
                    "VNPay payment URL must use HTTPS");
        }
    }

    @Override
    public String toString() {
        return "VnpayProperties[credentials=REDACTED]";
    }
}
