package com.structure.springboot.features.order.service;

import com.structure.springboot.features.order.config.OrderExpiryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bộ đếm trong bộ nhớ chặn việc hỏi VNPay quá nhiều lần cho cùng một đơn.
 *
 * <p>Đây là thứ đứng giữa "đơn chưa có kết luận" và "gọi VNPay mỗi phút mãi mãi". Sai ở đây không làm
 * hỏng dữ liệu, nhưng làm cạn hạn mức gọi API — và tệ hơn, làm chậm những đơn khác trong cùng lượt
 * quét.</p>
 */
class OrderExpiryCooldownTest {

    private static final Instant T0 = Instant.parse("2026-10-28T10:00:00Z");

    private final UUID orderId = UUID.randomUUID();

    private OrderExpiryCooldown cooldown(int maxQueryAttempts, Duration retryDelay) {
        return new OrderExpiryCooldown(new OrderExpiryProperties(
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                retryDelay,
                100,
                maxQueryAttempts,
                24));
    }

    @Test
    @DisplayName("lần đầu: được hỏi")
    void theFirstAttemptIsAllowed() {
        assertThat(cooldown(5, Duration.ofMinutes(5)).tryAcquire(orderId, T0)).isTrue();
    }

    @Test
    @DisplayName("gọi lại ngay trong thời gian chờ: bị chặn")
    void aSecondCallWithinTheRetryDelayIsBlocked() {
        OrderExpiryCooldown cooldown = cooldown(5, Duration.ofMinutes(5));

        assertThat(cooldown.tryAcquire(orderId, T0)).isTrue();
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofSeconds(30)))).isFalse();
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(4)))).isFalse();
    }

    @Test
    @DisplayName("hết thời gian chờ: được hỏi lại, và có đếm")
    void aCallAfterTheRetryDelayIsAllowed() {
        OrderExpiryCooldown cooldown = cooldown(5, Duration.ofMinutes(5));

        assertThat(cooldown.tryAcquire(orderId, T0)).isTrue();
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(5)))).isTrue();
        assertThat(cooldown.failures(orderId)).isEqualTo(2);
    }

    @Test
    @DisplayName("chạm trần số lần thử: THÔI hỏi, kể cả khi đã hết thời gian chờ")
    void onceExhaustedTheOrderIsNeverQueriedAgain() {
        OrderExpiryCooldown cooldown = cooldown(3, Duration.ofMinutes(1));

        assertThat(cooldown.tryAcquire(orderId, T0)).isTrue();
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(1)))).isTrue();
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(2)))).isTrue();

        // Đã đủ 3 lần ⇒ mọi lần sau đều bị chặn, dù thời gian chờ đã qua rất lâu.
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(3)))).isFalse();
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofDays(1)))).isFalse();
        assertThat(cooldown.isExhausted(orderId)).isTrue();
        assertThat(cooldown.failures(orderId)).isEqualTo(3);
    }

    @Test
    @DisplayName("số lần thử ghi nhận NGAY khi cấp quyền, không đợi có kết quả")
    void theAttemptIsCountedBeforeTheCallHappens() {
        OrderExpiryCooldown cooldown = cooldown(5, Duration.ofMinutes(1));

        cooldown.tryAcquire(orderId, T0);

        // Nếu đếm sau khi có kết quả thì một câu hỏi ném lỗi sẽ không được đếm, và trần không bao
        // giờ chạm tới — đúng thứ trần sinh ra để chặn.
        assertThat(cooldown.failures(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("forget() xoá sạch: đơn đã có kết luận không chiếm bộ nhớ và được hỏi lại từ đầu")
    void forgetClearsEverything() {
        OrderExpiryCooldown cooldown = cooldown(3, Duration.ofMinutes(1));

        cooldown.tryAcquire(orderId, T0);
        cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(1)));
        cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(2)));
        assertThat(cooldown.isExhausted(orderId)).isTrue();

        cooldown.forget(orderId);

        assertThat(cooldown.failures(orderId)).isZero();
        assertThat(cooldown.isExhausted(orderId)).isFalse();
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(3)))).isTrue();
    }

    @Test
    @DisplayName("hai đơn khác nhau không ảnh hưởng nhau")
    void ordersAreIndependent() {
        OrderExpiryCooldown cooldown = cooldown(2, Duration.ofMinutes(1));
        UUID other = UUID.randomUUID();

        assertThat(cooldown.tryAcquire(orderId, T0)).isTrue();
        assertThat(cooldown.tryAcquire(orderId, T0.plus(Duration.ofMinutes(1)))).isTrue();
        assertThat(cooldown.isExhausted(orderId)).isTrue();

        assertThat(cooldown.tryAcquire(other, T0.plus(Duration.ofMinutes(1)))).isTrue();
        assertThat(cooldown.isExhausted(other)).isFalse();
    }

    @Test
    @DisplayName("orderId null: chặn, không ném")
    void aNullOrderIsRejected() {
        OrderExpiryCooldown cooldown = cooldown(5, Duration.ofMinutes(1));

        assertThat(cooldown.tryAcquire(null, T0)).isFalse();
        cooldown.forget(null);
        assertThat(cooldown.failures(null)).isZero();
    }

    @Test
    @DisplayName("cấu hình sai lúc dựng properties: ném ngay, không chạy sai âm thầm")
    void invalidConfigurationFailsFast() {
        assertThatThrownBy(() -> new OrderExpiryProperties(
                Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(5), 100, 5, 24))
                .as("quét dày hơn 30 giây")
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OrderExpiryProperties(
                Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(5), 0, 5, 24))
                .as("lô rỗng")
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OrderExpiryProperties(
                Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(5), 100, 0, 24))
                .as("không cho thử lần nào")
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OrderExpiryProperties(
                Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(5), 100, 5, 0))
                .as("cận dưới thời gian bằng 0")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("giá trị mặc định khi thiếu cấu hình khớp với application.yaml")
    void defaultsMatchTheYaml() {
        OrderExpiryProperties defaults = new OrderExpiryProperties(null, null, null, null, null, null);

        assertThat(defaults.scanInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(defaults.scanInitialDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.batchSize()).isEqualTo(100);
        assertThat(defaults.maxQueryAttempts()).isEqualTo(5);
        assertThat(defaults.maxAgeHours()).isEqualTo(24);
    }
}
