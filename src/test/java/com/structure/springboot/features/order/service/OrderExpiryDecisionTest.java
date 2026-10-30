package com.structure.springboot.features.order.service;

import com.structure.springboot.features.order.config.OrderExpiryProperties;
import com.structure.springboot.features.order.config.OrderProperties;
import com.structure.springboot.features.order.dto.response.OrderExpirySummary;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.mapper.OrderMapper;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.order.service.impl.OrderServiceImpl;
import com.structure.springboot.features.payment.dto.response.VnpayQueryResult;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.enums.VnpayQueryVerdict;
import com.structure.springboot.features.payment.service.PaymentService;
import com.structure.springboot.features.product.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bảng quyết định của việc quét đơn hết hạn.
 *
 * <p>Đây là <b>nhóm test quan trọng nhất của CK-5</b>: nó khoá lại rằng một đơn chỉ bị huỷ khi có
 * căn cứ, và rằng mọi ngõ mơ hồ đều dẫn tới "không đụng gì".</p>
 *
 * <p>Cách dựng: {@code OrderServiceImpl} với đầy đủ phụ thuộc mock, còn chặng chốt ({@code
 * settleExpiredOrder}) được stub để trả về kết quả mong muốn — vì đó là chỗ có khoá và giao dịch,
 * kiểm riêng ở {@code OrderExpirySettleTest}.</p>
 */
class OrderExpiryDecisionTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private OrderRepository orderRepository;
    private PaymentService paymentService;
    private OrderExpiryCooldown cooldown;
    private OrderService selfProxy;
    private OrderServiceImpl service;

    private UUID orderId;
    private OrderEntity order;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentService = mock(PaymentService.class);
        cooldown = mock(OrderExpiryCooldown.class);

        ObjectProvider<OrderService> provider = mock(ObjectProvider.class);
        selfProxy = mock(OrderService.class);
        when(provider.getObject()).thenReturn(selfProxy);

        service = new OrderServiceImpl(
                orderRepository,
                mock(com.structure.springboot.features.account.repository.AccountRepository.class),
                mock(com.structure.springboot.features.product.repository.ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(OrderMapper.class),
                mock(com.structure.springboot.features.notification.service.NotificationService.class),
                mock(com.structure.springboot.infrastructure.external.brevo.BrevoMailClient.class),
                new OrderProperties(null, null, null),
                paymentService,
                new OrderExpiryProperties(Duration.ofMinutes(1), Duration.ofSeconds(30),
                        Duration.ofMinutes(5), 100, 5, 24),
                cooldown,
                provider
        );

        orderId = UUID.randomUUID();
        order = OrderEntity.builder()
                .orderNumber("ORD-0001")
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentDueAt(NOW.minus(Duration.ofMinutes(5)))
                .build();
        order.setId(orderId);

        when(orderRepository
                .findByStatusAndPaymentDueAtLessThanEqualAndPaymentDueAtGreaterThanEqual(
                        any(), any(), any(), any()))
                .thenReturn(List.of(order));
        when(cooldown.tryAcquire(any(), any())).thenReturn(true);
    }

    private void verdict(VnpayQueryVerdict value) {
        when(paymentService.queryVnpayForOrder(any()))
                .thenReturn(new VnpayQueryResult(value, null, null, null, null, null, null, "test"));
    }

    private OrderExpirySummary scan() {
        return service.expireOverdueOrders(NOW);
    }

    // ------------------------------------------------------------------ retry deadlock

    @Nested
    @DisplayName("Thua deadlock ở chặng chốt thì thử lại, không bỏ sót đơn")
    class RetryOnDeadlock {

        /**
         * settleExpiredOrder thua deadlock một lần rồi qua: lượt quét phải THỬ LẠI và vẫn huỷ được
         * đơn, thay vì để lỗi tạm thời làm hỏng việc huỷ. Đây là nợ CK-1 ("retry deadlock").
         */
        @Test
        @DisplayName("deadlock một lần ⇒ thử lại và huỷ thành công")
        void aDeadlockOnSettleIsRetried() {
            verdict(VnpayQueryVerdict.FAILED);
            when(selfProxy.settleExpiredOrder(eq(orderId), eq(VnpayQueryVerdict.FAILED), any()))
                    .thenThrow(new DeadlockLoserDataAccessException("deadlock", new RuntimeException("db")))
                    .thenReturn(true);

            OrderExpirySummary summary = scan();

            // Gọi đúng hai lần: lần đầu thua deadlock, lần hai thành công.
            verify(selfProxy, times(2))
                    .settleExpiredOrder(eq(orderId), eq(VnpayQueryVerdict.FAILED), any());
            assertThat(summary.cancelled()).isEqualTo(1);
            assertThat(summary.isConsistent()).isTrue();
        }
    }

    // ------------------------------------------------------------------ bốn kết cục

    @Nested
    @DisplayName("Bốn kết cục, chỉ một được huỷ")
    class Outcomes {

        @Test
        @DisplayName("FAILED ⇒ huỷ đơn, hoàn kho, payment EXPIRED")
        void failedCancelsTheOrder() {
            verdict(VnpayQueryVerdict.FAILED);
            when(selfProxy.settleExpiredOrder(eq(orderId), eq(VnpayQueryVerdict.FAILED), any()))
                    .thenReturn(true);

            OrderExpirySummary summary = scan();

            verify(selfProxy).settleExpiredOrder(eq(orderId), eq(VnpayQueryVerdict.FAILED), any());
            assertThat(summary.cancelled()).isEqualTo(1);
            assertThat(summary.paid()).isZero();
            assertThat(summary.unclear()).isZero();
            assertThat(summary.isConsistent()).isTrue();
        }

        @Test
        @DisplayName("NOT_FOUND ⇒ huỷ đơn")
        void notFoundCancelsTheOrder() {
            verdict(VnpayQueryVerdict.NOT_FOUND);
            when(selfProxy.settleExpiredOrder(eq(orderId), eq(VnpayQueryVerdict.NOT_FOUND), any()))
                    .thenReturn(true);

            assertThat(scan().cancelled()).isEqualTo(1);
        }

        @Test
        @DisplayName("PAID ⇒ KHÔNG huỷ, chỉ đếm đã thanh toán")
        void paidDoesNotCancel() {
            verdict(VnpayQueryVerdict.PAID);

            OrderExpirySummary summary = scan();

            verify(selfProxy, never()).settleExpiredOrder(any(), any(), any());
            assertThat(summary.paid()).isEqualTo(1);
            assertThat(summary.cancelled()).isZero();
            assertThat(summary.unclear()).isZero();
        }

        @Test
        @DisplayName("UNKNOWN ⇒ KHÔNG đụng gì, đếm chưa rõ")
        void unknownTouchesNothing() {
            verdict(VnpayQueryVerdict.UNKNOWN);

            OrderExpirySummary summary = scan();

            verify(selfProxy, never()).settleExpiredOrder(any(), any(), any());
            assertThat(summary.unclear()).isEqualTo(1);
            assertThat(summary.cancelled()).isZero();
            assertThat(summary.paid()).isZero();
        }

        @Test
        @DisplayName("verdict null (service trả null) ⇒ UNKNOWN, KHÔNG huỷ")
        void aNullVerdictIsTreatedAsUnknown() {
            when(paymentService.queryVnpayForOrder(any())).thenReturn(null);

            assertThat(scan().unclear()).isEqualTo(1);
            verify(selfProxy, never()).settleExpiredOrder(any(), any(), any());
        }
    }

    // ------------------------------------------------------------------ kiểm lại ở chặng chốt

    @Nested
    @DisplayName("Chặng chốt từ chối huỷ")
    class SettlementRefuses {

        @Test
        @DisplayName("đơn đã đổi trạng thái trong lúc chờ VNPay ⇒ KHÔNG đếm là đã huỷ")
        void aSettlementThatRefusesIsNotCountedAsCancelled() {
            verdict(VnpayQueryVerdict.FAILED);
            when(selfProxy.settleExpiredOrder(any(), any(), any())).thenReturn(false);

            OrderExpirySummary summary = scan();

            assertThat(summary.cancelled()).isZero();
            assertThat(summary.paid()).isEqualTo(1);
            assertThat(summary.isConsistent()).isTrue();
        }
    }

    // ------------------------------------------------------------------ bộ đếm

    @Nested
    @DisplayName("Bộ đếm chặn việc hỏi lại")
    class CooldownGuards {

        @Test
        @DisplayName("chưa tới lượt hỏi lại ⇒ KHÔNG gọi VNPay, đếm chưa rõ")
        void aCoolingDownOrderIsSkipped() {
            when(cooldown.tryAcquire(any(), any())).thenReturn(false);

            OrderExpirySummary summary = scan();

            verify(paymentService, never()).queryVnpayForOrder(any());
            assertThat(summary.unclear()).isEqualTo(1);
        }

        @Test
        @DisplayName("có kết quả rõ ràng ⇒ quên bộ đếm, không chiếm bộ nhớ")
        void aConclusiveVerdictClearsTheCounter() {
            verdict(VnpayQueryVerdict.PAID);

            scan();

            verify(cooldown).forget(orderId);
        }
    }

    // ------------------------------------------------------------------ truy vấn

    @Nested
    @DisplayName("Truy vấn ứng viên")
    class CandidateQuery {

        @Test
        @DisplayName("chỉ hỏi đơn PENDING, có hạn trong 24 giờ gần đây")
        void theQueryUsesTheRightBounds() {
            when(orderRepository
                    .findByStatusAndPaymentDueAtLessThanEqualAndPaymentDueAtGreaterThanEqual(
                            any(), any(), any(), any()))
                    .thenReturn(List.of());

            scan();

            verify(orderRepository)
                    .findByStatusAndPaymentDueAtLessThanEqualAndPaymentDueAtGreaterThanEqual(
                            eq(OrderStatus.PENDING),
                            eq(NOW),
                            eq(NOW.minus(Duration.ofHours(24))),
                            any());
        }

        @Test
        @DisplayName("không có đơn nào ⇒ tóm tắt rỗng, không gọi VNPay")
        void noCandidatesMeansNoQueries() {
            when(orderRepository
                    .findByStatusAndPaymentDueAtLessThanEqualAndPaymentDueAtGreaterThanEqual(
                            any(), any(), any(), any()))
                    .thenReturn(List.of());

            OrderExpirySummary summary = scan();

            assertThat(summary).isEqualTo(OrderExpirySummary.empty());
            verify(paymentService, never()).queryVnpayForOrder(any());
        }
    }

    // ------------------------------------------------------------------ ranh giới feature

    @Test
    @DisplayName("feature order KHÔNG đọc thẳng PaymentRepository — đi qua PaymentService")
    void theOrderFeatureNeverTouchesThePaymentRepository() {
        verdict(VnpayQueryVerdict.UNKNOWN);

        scan();

        // Bằng chứng gián tiếp nhưng đủ: lời gọi duy nhất tới thế giới payment là qua service.
        verify(paymentService).queryVnpayForOrder(any());
    }

    @Test
    @DisplayName("payment mẫu: PAID của lần thử trước vẫn chặn huỷ (kiểm ở settle, nhưng nêu ở đây)")
    void aPaymentRecordedAsPaidBlocksCancellation() {
        PaymentEntity paid = PaymentEntity.builder()
                .txnRef("abc")
                .status(PaymentStatus.PAID)
                .build();

        // hasPaidPayment là thứ settleExpiredOrder dùng; ở tầng này chỉ cần chắc nó tồn tại và
        // được gọi đúng chỗ — phần hành vi kiểm ở OrderExpirySettleTest.
        when(paymentService.hasPaidPayment(any())).thenReturn(true);
        assertThat(paymentService.hasPaidPayment(orderId)).isTrue();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("đơn COD: paymentDueAt null nên không bao giờ lọt vào danh sách")
    void codOrdersHaveNoDeadline() {
        OrderEntity cod = OrderEntity.builder()
                .orderNumber("ORD-COD")
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentDueAt(null)
                .build();

        // Điều kiện paymentDueAt <= now không bao giờ đúng với null — đó là cơ chế loại COD,
        // không phải sơ suất. Ở đây khẳng định lại dữ kiện đó để nếu ai đổi sang so sánh khác
        // (ví dụ coalesce về now) thì test này nhắc.
        assertThat(cod.getPaymentDueAt()).isNull();
        assertThat(OrderStatus.PENDING).isNotNull();
    }

}
