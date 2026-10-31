package com.structure.springboot.features.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật hạ tầng chạy theo lịch và đăng ký {@link OrderExpiryProperties}.
 *
 * <h2>Vì sao {@code @EnableScheduling} nằm ở đây chứ không ở lớp {@code Application}</h2>
 *
 * <p>Cùng lý do như {@code BrevoConfig} đăng ký {@code BrevoProperties}: cấu hình của một feature
 * nằm trong chính feature đó. Đặt ở lớp khởi động thì người đọc feature order không thấy được rằng
 * nó có một tác vụ chạy nền — và tệ hơn, bật {@code @EnableScheduling} ở tầng gốc là mở đường cho
 * bất kỳ feature nào thêm {@code @Scheduled} mà không ai để ý. Một tác vụ chạy nền sai không làm
 * ứng dụng chết, chỉ lặng lẽ làm sai dữ liệu.</p>
 *
 * <p>Đây là <b>lần đầu</b> dự án dùng lịch chạy nền: trước CK-5 không có {@code @EnableScheduling}
 * và không có {@code @Scheduled} nào trong mã nguồn.</p>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrderExpiryProperties.class)
public class OrderSchedulingConfig {
}
