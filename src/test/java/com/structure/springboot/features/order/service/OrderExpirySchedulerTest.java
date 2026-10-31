package com.structure.springboot.features.order.service;

import com.structure.springboot.features.order.dto.response.OrderExpirySummary;
import com.structure.springboot.features.order.scheduler.OrderExpiryScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tác vụ nền: chỉ kiểm nó gọi đúng một lần và <b>không chết</b>.
 *
 * <p>Logic nằm ở service, không ở đây — nên test này cố ý mỏng. Thứ duy nhất nó phải bảo vệ là bất
 * biến vận hành quan trọng nhất: một lượt quét hỏng <b>không được</b> làm chết tác vụ lặp.</p>
 */
class OrderExpirySchedulerTest {

    @Test
    @DisplayName("gọi service đúng một lần mỗi lượt, với mốc thời gian thực")
    void theSchedulerCallsTheServiceOnce() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.expireOverdueOrders(any(Instant.class)))
                .thenReturn(OrderExpirySummary.empty());

        new OrderExpiryScheduler(orderService).scan();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(orderService).expireOverdueOrders(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("service ném ngoại lệ: tác vụ KHÔNG chết, nuốt và ghi log")
    void anExceptionDoesNotKillTheSchedule() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.expireOverdueOrders(any(Instant.class)))
                .thenThrow(new IllegalStateException("database down"));

        // Không ném ra ngoài: lượt sau vẫn phải chạy. Nếu ném, Spring chỉ ghi log rồi tiếp — nhưng
        // hành vi đó phụ thuộc cấu hình scheduler, không phải thứ ta muốn dựa vào.
        assertThatCode(() -> new OrderExpiryScheduler(orderService).scan())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bản tóm tắt: checked = cancelled + paid + unclear")
    void theSummaryIsConsistent() {
        OrderExpirySummary summary = new OrderExpirySummary(10, 4, 3, 3);

        assertThat(summary.isConsistent()).isTrue();
        assertThat(summary.toLogLine())
                .contains("checked=10")
                .contains("cancelled=4")
                .contains("paid=3")
                .contains("unclear=3");
    }

    @Test
    @DisplayName("lượt không có đơn nào vẫn log được")
    void anEmptyScanStillProducesALogLine() {
        OrderExpirySummary empty = OrderExpirySummary.empty();

        assertThat(empty.isConsistent()).isTrue();
        assertThat(empty.toLogLine()).contains("checked=0");
    }

    @Test
    @DisplayName("lỗi hạ tầng giữa vòng lặp: tác vụ nuốt và đi tiếp, lượt sau chạy lại")
    void anInfrastructureFailureDoesNotEscape() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.expireOverdueOrders(any(Instant.class)))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("connection lost"));

        assertThatCode(() -> new OrderExpiryScheduler(orderService).scan())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OrderExpiryScheduler LÀ lớp bắt lỗi — không phải expireOverdueOrders")
    void theSchedulerIsTheOneThatCatches() throws Exception {
        // Bất biến này từng bị ghi sai trong javadoc của OrderService: nó khẳng định "lỗi của
        // từng đơn được đếm và ghi log, rồi đi tiếp", trong khi vòng lặp không có try/catch nào.
        // Test này khoá lại kiến trúc THẬT: lớp scheduler bắt, service không bắt.
        String scheduler = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/structure/springboot/features/order/scheduler/OrderExpiryScheduler.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        String service = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/structure/springboot/features/order/service/impl/OrderServiceImpl.java"),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(scheduler).contains("catch (RuntimeException");
        assertThat(scheduler).contains("log.error");

        int loopStart = service.indexOf("for (OrderEntity candidate : candidates)");
        int loopEnd = service.indexOf("return new OrderExpirySummary(", loopStart);
        String loop = service.substring(loopStart, loopEnd);

        assertThat(loop)
                .as("vòng lặp KHÔNG được có try/catch riêng — một lỗi hạ tầng phải nổi lên scheduler")
                .doesNotContain("try {")
                .doesNotContain("catch (");
    }
}
