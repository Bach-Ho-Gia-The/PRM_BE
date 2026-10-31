package com.structure.springboot.features.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Chính sách đặt hàng, gom từ {@code app.order.*}: phí ship, ngưỡng freeship, thời hạn thanh toán.
 *
 * <p>Để ngoài yaml vì đây là chính sách kinh doanh — bộ phận vận hành đổi ngưỡng freeship theo
 * từng đợt khuyến mãi, không thể mỗi lần đổi lại phải build lại ứng dụng.</p>
 *
 * <p><b>Vì sao thời hạn thanh toán ở đây chứ không ở {@code app.payment.vnpay}:</b> hạn thanh toán
 * là luật của <b>đơn hàng</b>, không phải của cổng thanh toán. VNPay chỉ là một kênh; đổi sang
 * kênh khác thì hạn giữ nguyên. Đặt ở đây cũng giữ đúng chiều phụ thuộc đã chốt:
 * {@code payment} biết {@code order}, còn {@code order} <b>không</b> biết cổng thanh toán nào.</p>
 *
 * <p>Compact constructor điền sẵn giá trị dự phòng theo đúng tiền lệ của {@code AuthCookieProperties},
 * nên thiếu key trong yaml cũng không NPE.</p>
 */
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(
        BigDecimal shippingFee,
        BigDecimal freeShippingThreshold,
        Duration paymentTimeout
) {

    /**
     * Trần thời hạn thanh toán, <b>15 phút</b>.
     *
     * <p><b>Vì sao có trần:</b> thời hạn này là thứ quyết định khách còn giữ được hàng hay không.
     * Một biến môi trường gõ nhầm — {@code 15s} thay vì {@code 15m}, hay {@code 15d} — sẽ khiến
     * mọi đơn tự huỷ gần như tức thì hoặc giữ hàng cả tháng, mà app vẫn khởi động bình thường.
     * Chặn ở đây để sai cấu hình thì <b>không start được</b>, giống cách {@code VnpayProperties}
     * kiểm URL.</p>
     */
    private static final Duration MAX_PAYMENT_TIMEOUT = Duration.ofMinutes(30);

    public OrderProperties {
        shippingFee = shippingFee != null ? shippingFee : new BigDecimal("30000");
        freeShippingThreshold = freeShippingThreshold != null ? freeShippingThreshold : new BigDecimal("1000000");
        paymentTimeout = paymentTimeout != null ? paymentTimeout : Duration.ofMinutes(15);

        if (paymentTimeout.isNegative() || paymentTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "app.order.payment-timeout must be positive");
        }
        if (paymentTimeout.compareTo(MAX_PAYMENT_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "app.order.payment-timeout must not exceed " + MAX_PAYMENT_TIMEOUT);
        }
    }
}
