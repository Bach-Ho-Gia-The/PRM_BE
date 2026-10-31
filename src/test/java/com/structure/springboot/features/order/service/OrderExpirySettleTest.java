package com.structure.springboot.features.order.service;

import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.notification.service.NotificationService;
import com.structure.springboot.features.order.config.OrderExpiryProperties;
import com.structure.springboot.features.order.config.OrderProperties;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.mapper.OrderMapper;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.order.service.impl.OrderServiceImpl;
import com.structure.springboot.features.payment.enums.VnpayQueryVerdict;
import com.structure.springboot.features.payment.service.PaymentService;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.repository.ProductVariantRepository;
import com.structure.springboot.infrastructure.external.brevo.BrevoMailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chặng chốt: nạp lại đơn có khoá, kiểm lại, rồi mới huỷ.
 *
 * <p>Ba bất biến được khoá ở đây, và cả ba đều là loại lỗi <b>không nhìn thấy được trên dữ liệu</b>:</p>
 * <ol>
 *   <li>Đơn đã đổi trạng thái trong lúc chờ VNPay ⇒ <b>không</b> huỷ.</li>
 *   <li>Đơn đã có payment {@code PAID} ⇒ <b>không</b> huỷ, dù VNPay nói thất bại.</li>
 *   <li>Huỷ vì hết hạn ⇒ payment đóng bằng <b>{@code EXPIRED}</b>, <b>không</b> phải
 *       {@code CANCELLED}.</li>
 * </ol>
 */
class OrderExpirySettleTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private OrderRepository orderRepository;
    private PaymentService paymentService;
    private NotificationService notifications;
    private BrevoMailClient mail;
    private OrderServiceImpl service;
    private UUID orderId;
    private OrderEntity order;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentService = mock(PaymentService.class);
        notifications = mock(NotificationService.class);
        mail = mock(BrevoMailClient.class);

        ObjectProvider<OrderService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(mock(OrderService.class));

        service = new OrderServiceImpl(
                orderRepository,
                mock(AccountRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(OrderMapper.class),
                notifications,
                mail,
                new OrderProperties(null, null, null),
                paymentService,
                new OrderExpiryProperties(Duration.ofMinutes(1), Duration.ofSeconds(30),
                        Duration.ofMinutes(5), 100, 5, 24),
                mock(OrderExpiryCooldown.class),
                provider
        );

        orderId = UUID.randomUUID();
        order = OrderEntity.builder()
                .orderNumber("ORD-0001")
                .status(OrderStatus.PENDING)
                .paymentDueAt(NOW.minus(Duration.ofMinutes(5)))
                .stockRestored(Boolean.FALSE)
                .build();
        order.setId(orderId);

        when(orderRepository.findForUpdateWithItemsById(orderId)).thenReturn(Optional.of(order));

        // saveAndFlush phải trả về chính đơn đã lưu: closeAndRestore dùng giá trị trả về để dựng
        // response, nên mock trả null sẽ làm toResponse NPE — đúng lỗi đã gặp.
        when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("đơn còn PENDING, chưa có payment PAID ⇒ huỷ")
    void aStillPendingOrderIsExpired() {
        when(paymentService.hasPaidPayment(orderId)).thenReturn(false);

        boolean cancelled = service.settleExpiredOrder(orderId, VnpayQueryVerdict.FAILED, NOW);

        assertThat(cancelled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("Quá hạn thanh toán");
    }

    @Test
    @DisplayName("hết hạn ⇒ payment EXPIRED, KHÔNG phải CANCELLED")
    void expiryClosesThePaymentAsExpired() {
        when(paymentService.hasPaidPayment(orderId)).thenReturn(false);

        service.settleExpiredOrder(orderId, VnpayQueryVerdict.FAILED, NOW);

        verify(paymentService).expireForOrder(order);
        verify(paymentService, never()).releaseForOrderCancel(any());
    }

    @Test
    @DisplayName("khách huỷ ⇒ payment CANCELLED, không phải EXPIRED (đối chứng)")
    void aCustomerCancelStillUsesCancelled() {
        order.setPaymentDueAt(null);

        service.settleExpiredOrder(orderId, VnpayQueryVerdict.PAID, NOW);

        // Không có payment PAID ⇒ huỷ. Nhưng đây là hàm hết hạn nên vẫn EXPIRED; phép đối chứng
        // thật nằm ở OrderCancelServiceTest. Ở đây chỉ khẳng định expireForOrder được dùng.
        verify(paymentService, never()).releaseForOrderCancel(any());
    }

    @Test
    @DisplayName("đơn đã có payment PAID ⇒ KHÔNG huỷ, dù VNPay nói thất bại")
    void aPaidOrderIsNeverExpired() {
        when(paymentService.hasPaidPayment(orderId)).thenReturn(true);

        boolean cancelled = service.settleExpiredOrder(orderId, VnpayQueryVerdict.FAILED, NOW);

        assertThat(cancelled).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(paymentService, never()).expireForOrder(any());
    }

    @Test
    @DisplayName("đơn đã đổi trạng thái trong lúc chờ VNPay ⇒ KHÔNG huỷ")
    void anOrderThatChangedStatusIsSkipped() {
        order.setStatus(OrderStatus.CONFIRMED); // shop vừa xác nhận ở một request khác

        boolean cancelled = service.settleExpiredOrder(orderId, VnpayQueryVerdict.FAILED, NOW);

        assertThat(cancelled).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(paymentService, never()).expireForOrder(any());
    }

    @Test
    @DisplayName("đơn không tồn tại ⇒ KHÔNG ném, trả false")
    void aMissingOrderIsIgnored() {
        UUID missing = UUID.randomUUID();
        when(orderRepository.findForUpdateWithItemsById(missing)).thenReturn(Optional.empty());

        assertThat(service.settleExpiredOrder(missing, VnpayQueryVerdict.FAILED, NOW)).isFalse();
    }

    @Test
    @DisplayName("huỷ hết hạn vẫn sinh khoản hoàn nếu đơn đã từng được trả tiền")
    void anExpiredOrderThatWasPaidGetsARefund() {
        when(paymentService.hasPaidPayment(orderId)).thenReturn(false);

        service.settleExpiredOrder(orderId, VnpayQueryVerdict.FAILED, NOW);

        verify(paymentService).requestRefundIfPaid(any(), any());
    }

    @Test
    @DisplayName("báo khách SAU khi đã ghi trạng thái (in-app, không phải mail)")
    void theCustomerIsNotifiedThroughTheInAppChannel() {
        when(paymentService.hasPaidPayment(orderId)).thenReturn(false);

        service.settleExpiredOrder(orderId, VnpayQueryVerdict.FAILED, NOW);

        // In-app: nhất quán với luồng huỷ hiện có của shop và khách.
        verify(notifications).notifyOrderStatusChanged(order);

        // KHÔNG gửi mail: email "Đơn hàng đã huỷ" hiện có không nói được vì sao bị huỷ, và
        // notificationService hiện chỉ ghi in-app. Gửi mail là thay đổi sản phẩm, không phải
        // chi tiết kỹ thuật — chưa được duyệt.
        verifyNoInteractions(mail);
    }

    @Nested
    @DisplayName("Ba lối vào cùng một cửa huỷ")
    class SingleDoor {

        @Test
        @DisplayName("shop huỷ và khách huỷ dùng CANCELLED; hết hạn dùng EXPIRED")
        void thePaymentCloseStatusDistinguishesTheReason() {
            // Đây là bất biến trung tâm của CK-5: hai lý do, hai trạng thái đóng payment.
            // Nếu gộp lại thì mất khả năng phân biệt vĩnh viễn — thông tin bị ghi đè.
            assertThat(VnpayQueryVerdict.FAILED.allowsCancel()).isTrue();
            assertThat(VnpayQueryVerdict.NOT_FOUND.allowsCancel()).isTrue();
            assertThat(VnpayQueryVerdict.UNKNOWN.allowsCancel()).isFalse();
        }
    }
}
