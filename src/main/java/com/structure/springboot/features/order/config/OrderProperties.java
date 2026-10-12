package com.structure.springboot.features.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Quy tắc tính phí vận chuyển, gom từ {@code app.order.*}.
 *
 * <p>Để ngoài yaml vì đây là chính sách kinh doanh — bộ phận vận hành đổi ngưỡng freeship theo
 * từng đợt khuyến mãi, không thể mỗi lần đổi lại phải build lại ứng dụng.</p>
 *
 * <p>Compact constructor điền sẵn giá trị dự phòng theo đúng tiền lệ của {@code AuthCookieProperties},
 * nên thiếu key trong yaml cũng không NPE.</p>
 */
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(
        BigDecimal shippingFee,
        BigDecimal freeShippingThreshold
) {

    public OrderProperties {
        shippingFee = shippingFee != null ? shippingFee : new BigDecimal("30000");
        freeShippingThreshold = freeShippingThreshold != null ? freeShippingThreshold : new BigDecimal("1000000");
    }
}
