package com.structure.springboot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Nạp toàn bộ Spring context để bắt sớm những lỗi chỉ lộ ra lúc khởi động: thiếu bean, sai
 * {@code @ConfigurationProperties}, entity map hỏng, cấu hình security vòng lặp...
 *
 * <p><b>Hạ tầng do Testcontainers tự dựng</b> — PostgreSQL và Redis thật chạy trong Docker, xoá
 * sạch sau khi test xong. {@code @ServiceConnection} tự bơm host/port/user/pass của container vào
 * Spring, nên KHÔNG cần khai báo {@code spring.datasource.*} hay {@code @DynamicPropertySource}.</p>
 *
 * <p><b>{@code disabledWithoutDocker = true}</b>: máy nào không có Docker (và cả sandbox build)
 * thì test tự bỏ qua thay vì đỏ. Đổi lại, đừng coi "build xanh" trên máy không Docker là bằng
 * chứng context khởi động được — phải chạy ở nơi có Docker.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class BeFormatApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    /**
     * Redis không có container chuyên dụng trong Testcontainers nên dùng {@link GenericContainer}.
     * {@code name = "redis"} là thứ cho Spring biết đây là Redis để tự nạp {@code spring.data.redis.*};
     * thiếu nó thì ứng dụng vẫn đi tìm Redis ở localhost:6379.
     */
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Test
    void contextLoads() {
    }

}
