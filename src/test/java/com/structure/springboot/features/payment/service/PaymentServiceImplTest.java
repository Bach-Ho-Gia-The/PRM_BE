package com.structure.springboot.features.payment.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.payment.dto.response.PaymentResponse;
import com.structure.springboot.features.payment.dto.response.VnpayPaymentUrlResponse;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.mapper.PaymentMapper;
import com.structure.springboot.features.payment.repository.PaymentRepository;
import com.structure.springboot.features.payment.service.impl.PaymentServiceImpl;
import com.structure.springboot.infrastructure.external.vnpay.VnpayPaymentUrlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nghiệp vụ thanh toán.
 *
 * <p>Nhan test de ASCII thuan vi output surefire ma hoa hong tieng Viet co dau.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository payments;
    @Mock private OrderRepository orders;
    @Mock private VnpayPaymentUrlBuilder urlBuilder;

    private PaymentServiceImpl service;

    private final UUID me = UUID.randomUUID();
    private static final String ORDER_NO = "LBVN-2610-A7K2";

    @BeforeEach
    void setUp() {
        PaymentMapper mapper = Mappers.getMapper(PaymentMapper.class);
        service = new PaymentServiceImpl(payments, orders, mapper, urlBuilder);
    }

    private OrderEntity order(PaymentMethod method, OrderStatus status, Instant dueAt) {
        AccountEntity account = new AccountEntity();
        account.setId(me);
        OrderEntity entity = OrderEntity.builder()
                .orderNumber(ORDER_NO).account(account).status(status).paymentMethod(method)
                .subtotal(new BigDecimal("1000000")).shippingFee(new BigDecimal("30000"))
                .discount(BigDecimal.ZERO).total(new BigDecimal("1030000"))
                .paymentDueAt(dueAt)
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private OrderEntity payableOrder() {
        return order(PaymentMethod.BANK_TRANSFER, OrderStatus.PENDING,
                Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    private PaymentEntity attempt(PaymentStatus status, int attemptNo, OrderEntity order) {
        PaymentEntity entity = PaymentEntity.builder()
                .order(order).txnRef("ref-" + attemptNo).amount(order.getTotal())
                .status(status).expiresAt(order.getPaymentDueAt()).attemptNo(attemptNo).build();
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        return entity;
    }

    private void stubOrder(OrderEntity order) {
        when(orders.findByOrderNumberAndAccountIdForUpdate(ORDER_NO, me)).thenReturn(Optional.of(order));
    }

    private void echoSave() {
        when(payments.save(any(PaymentEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void stubUrl() {
        when(urlBuilder.build(any())).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?sig");
    }

    // ───────────── tao lan thu dau tien khi dat don ─────────────

    /** Don chuyen khoan: payment PENDING, han SAO CHEP tu don, so tien la TONG don. */
    @Test
    void theFirstAttemptCopiesTheDeadlineAndTheOrderTotal() {
        Instant dueAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        OrderEntity order = order(PaymentMethod.BANK_TRANSFER, OrderStatus.PENDING, dueAt);
        echoSave();

        PaymentEntity created = service.createFirstAttempt(order);

        assertEquals(PaymentStatus.PENDING, created.getStatus());
        assertEquals(dueAt, created.getExpiresAt());
        assertEquals(1, created.getAttemptNo());
        // Tong don (1.030.000) chu KHONG phai subtotal (1.000.000).
        assertEquals(new BigDecimal("1030000"), created.getAmount());
        assertNotNull(created.getTxnRef());
        // txnRef phai la UUID bo dau gach: 32 ky tu hex, khong co '-'.
        assertTrue(created.getTxnRef().matches("[a-f0-9]{32}"));
    }

    /** COD: van co payment, nhung KHONG co han. */
    @Test
    void aCodOrderStillGetsAPaymentWithoutDeadline() {
        OrderEntity order = order(PaymentMethod.COD, OrderStatus.PENDING, null);
        echoSave();

        PaymentEntity created = service.createFirstAttempt(order);

        assertNull(created.getExpiresAt());
        assertEquals(PaymentStatus.PENDING, created.getStatus());
        assertEquals(1, created.getAttemptNo());
    }

    // ───────────── mo lai link: KHONG gia han ─────────────

    /** Dang PENDING: dung lai dung ban ghi cu, giu txnRef va han. */
    @Test
    void reopeningWhilePendingReusesTheSameAttemptAndKeepsTheOldDeadline() {
        OrderEntity order = payableOrder();
        PaymentEntity pending = attempt(PaymentStatus.PENDING, 1, order);
        stubOrder(order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId())).thenReturn(Optional.of(pending));
        stubUrl();

        VnpayPaymentUrlResponse response =
                service.startVnpayPayment(me, ORDER_NO, "127.0.0.1");

        assertEquals("ref-1", response.txnRef());
        assertEquals(pending.getExpiresAt(), response.expiresAt());
        // Khong sinh ban ghi moi.
        verify(payments, never()).save(any());
    }

    // ───────────── chi FAILED moi duoc thu lai ─────────────

    /** FAILED: mo lan thu moi, txnRef moi, nhung HAN VAN LA HAN CU cua don. */
    @Test
    void aFailedAttemptMayBeRetriedWithTheSameDeadline() {
        OrderEntity order = payableOrder();
        PaymentEntity failed = attempt(PaymentStatus.FAILED, 1, order);
        stubOrder(order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId())).thenReturn(Optional.of(failed));
        echoSave();
        stubUrl();

        service.startVnpayPayment(me, ORDER_NO, "127.0.0.1");

        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(payments).save(captor.capture());
        PaymentEntity fresh = captor.getValue();

        assertEquals(2, fresh.getAttemptNo());
        assertEquals(order.getPaymentDueAt(), fresh.getExpiresAt());
        // txnRef moi, khac lan truoc.
        assertNotNull(fresh.getTxnRef());
        assertEquals(32, fresh.getTxnRef().length());
    }

    /**
     * CANCELLED = khach CHU DONG huy thanh toan. Tuyet doi khong tu mo lan thu moi — day la dieu
     * kien loai tru cot loi so voi FAILED.
     */
    @Test
    void aCustomerCancelledPaymentIsNeverRetriedAutomatically() {
        OrderEntity order = payableOrder();
        stubOrder(order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(attempt(PaymentStatus.CANCELLED, 1, order)));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.startVnpayPayment(me, ORDER_NO, "127.0.0.1"));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("cancelled"));
        verify(payments, never()).save(any());
    }

    /** EXPIRED: don da huy va tra kho, khach phai dat don moi. */
    @Test
    void anExpiredPaymentCannotBeRestarted() {
        OrderEntity order = payableOrder();
        stubOrder(order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(attempt(PaymentStatus.EXPIRED, 1, order)));

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> service.startVnpayPayment(me, ORDER_NO, "127.0.0.1")).getErrorCode());
    }

    // ───────────── cac chan doan truoc khi mo link ─────────────

    @Test
    void aCodOrderCannotOpenAVnpayLink() {
        stubOrder(order(PaymentMethod.COD, OrderStatus.PENDING, null));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.startVnpayPayment(me, ORDER_NO, "127.0.0.1"));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("COD"));
    }

    @Test
    void aCancelledOrderCannotOpenAVnpayLink() {
        stubOrder(order(PaymentMethod.BANK_TRANSFER, OrderStatus.CANCELLED,
                Instant.now().plus(5, ChronoUnit.MINUTES)));

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> service.startVnpayPayment(me, ORDER_NO, "127.0.0.1")).getErrorCode());
    }

    /** Qua han thi bao "Payment has expired" du don chua kip bi tac vu quet doi trang thai. */
    @Test
    void aPastDeadlineIsRejectedEvenIfTheOrderIsStillPending() {
        stubOrder(order(PaymentMethod.BANK_TRANSFER, OrderStatus.PENDING,
                Instant.now().minus(1, ChronoUnit.SECONDS)));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.startVnpayPayment(me, ORDER_NO, "127.0.0.1"));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("expired"));
    }

    @Test
    void anAlreadyPaidOrderCannotOpenAVnpayLink() {
        OrderEntity order = payableOrder();
        stubOrder(order);
        when(payments.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PAID)).thenReturn(true);

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> service.startVnpayPayment(me, ORDER_NO, "127.0.0.1")).getErrorCode());
    }

    /** Don cua nguoi khac la 404, khong phai 403: khong lo don do co that hay khong. */
    @Test
    void someoneElsesOrderIsNotFound() {
        when(orders.findByOrderNumberAndAccountIdForUpdate("LBVN-2610-XXXX", me))
                .thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.startVnpayPayment(me, "LBVN-2610-XXXX", "127.0.0.1")).getErrorCode());
    }

    // ───────────── doc trang thai ─────────────

    @Test
    void thePaymentStatusIsReadableByItsOwner() {
        OrderEntity order = payableOrder();
        when(orders.findByOrderNumberAndAccountId(ORDER_NO, me)).thenReturn(Optional.of(order));
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(attempt(PaymentStatus.PENDING, 1, order)));

        PaymentResponse response = service.getMyPayment(me, ORDER_NO);

        assertEquals(ORDER_NO, response.orderNumber());
        assertEquals(PaymentStatus.PENDING, response.status());
        assertEquals(1, response.attemptNo());
    }
}
