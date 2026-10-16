package com.structure.springboot.infrastructure.external.brevo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Bật binding cho {@link BrevoProperties}, theo đúng tiền lệ của {@code OrderConfig}.
 *
 * <p>{@code @EnableAsync} đặt ở đây chứ không rải chỗ khác vì {@code BrevoMailClient} là nơi
 * DUY NHẤT trong dự án dùng {@code @Async} — gửi mail báo tin không được phép chặn nghiệp vụ.
 * Bộ thực thi thì để Spring Boot tự cấu hình, không tự dựng pool riêng.</p>
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(BrevoProperties.class)
public class BrevoConfig {
}
