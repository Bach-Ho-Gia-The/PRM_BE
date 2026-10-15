package com.structure.springboot.features.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link OrderProperties} ngay trong module order.
 *
 * <p>Không gắn vào {@code SecurityConfig} ở tầng {@code common} như mấy lớp properties của auth:
 * cấu hình của một feature thì nên nằm trong chính feature đó, để {@code common} không phải biết
 * tới sự tồn tại của {@code features.order}.</p>
 */
@Configuration
@EnableConfigurationProperties(OrderProperties.class)
public class OrderConfig {
}
