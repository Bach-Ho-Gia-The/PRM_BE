package com.structure.springboot.features.payment.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.payment.dto.projection.RefundAdminView;
import com.structure.springboot.features.payment.dto.response.PaymentResponse;
import com.structure.springboot.features.payment.dto.response.RefundAdminResponse;
import com.structure.springboot.features.payment.dto.response.VnpayPaymentUrlResponse;
import com.structure.springboot.features.payment.entity.OrderRefundEntity;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.enums.RefundStatus;
import com.structure.springboot.features.payment.mapper.PaymentMapper;
import com.structure.springboot.features.payment.repository.OrderRefundRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Nghiệp vụ thanh toán.
 *
 * <p>Nhan test de ASCII thuan vi output surefire ma hoa hong tieng Viet co dau.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository payments;
    @Mock private OrderRefundRepository refunds;
    @Mock private OrderRepository orders;
    @Mock private VnpayPaymentUrlBuilder urlBuilder;

    private PaymentServiceImpl service;

    private final UUID me = UUID.randomUUID();
    private static final String ORDER_NO = "LBVN-2610-A7K2";

    private final com.structure.springboot.features.payment.service.VnpayQueryService vnpayQueryService =
            mock(com.structure.springboot.features.payment.service.VnpayQueryService.class);

    @BeforeEach
    void setUp() {
        PaymentMapper mapper = Mappers.getMapper(PaymentMapper.class);
        service = new PaymentServiceImpl(payments, refunds, orders, mapper, urlBuilder, vnpayQueryService);
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
        when(orders.findForUpdateByOrderNumberAndAccountId(ORDER_NO, me)).thenReturn(Optional.of(order));
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
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-XXXX", me))
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

    // ───────────── dong payment khi don bi huy / het han ─────────────

    /** Khach huy don: lan thu dang mo phai thanh CANCELLED, khong duoc ket PENDING. */
    @Test
    void releasingAPendingPaymentMarksItCancelled() {
        OrderEntity order = payableOrder();
        PaymentEntity pending = attempt(PaymentStatus.PENDING, 1, order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(pending));

        service.releaseForOrderCancel(order);

        assertEquals(PaymentStatus.CANCELLED, pending.getStatus());
    }

    /**
     * Tien da ve thi KHONG duoc ghi de.
     * Ghi de o day la xoa mat su that quan trong nhat trong he thong: khach da tra tien.
     */
    @Test
    void releasingKeepsAPaidPaymentUntouched() {
        OrderEntity order = payableOrder();
        PaymentEntity paid = attempt(PaymentStatus.PAID, 1, order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(paid));

        service.releaseForOrderCancel(order);

        assertEquals(PaymentStatus.PAID, paid.getStatus());
    }

    /** Goi lai nhieu lan vo hai: dong roi thi khong lam gi them. */
    @Test
    void releasingIsIdempotent() {
        OrderEntity order = payableOrder();
        PaymentEntity pending = attempt(PaymentStatus.PENDING, 1, order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(pending));

        service.releaseForOrderCancel(order);
        service.releaseForOrderCancel(order);

        assertEquals(PaymentStatus.CANCELLED, pending.getStatus());
    }

    /** Don khong co ban ghi payment nao (du lieu tao truoc CK-2): khong nem loi. */
    @Test
    void releasingAnOrderWithoutAnyPaymentDoesNothing() {
        OrderEntity order = payableOrder();
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.empty());

        service.releaseForOrderCancel(order);

        verify(payments, never()).save(any());
    }

    /** Qua han thanh toan: lan thu dang mo thanh EXPIRED, khac han CANCELLED cua khach huy. */
    @Test
    void expiringAPendingPaymentMarksItExpired() {
        OrderEntity order = payableOrder();
        PaymentEntity pending = attempt(PaymentStatus.PENDING, 1, order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(pending));

        service.expireForOrder(order);

        assertEquals(PaymentStatus.EXPIRED, pending.getStatus());
    }

    /** Qua han nhung tien da ve trong gang tac: van khong duoc ghi de. */
    @Test
    void expiringKeepsAPaidPaymentUntouched() {
        OrderEntity order = payableOrder();
        PaymentEntity paid = attempt(PaymentStatus.PAID, 1, order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(paid));

        service.expireForOrder(order);

        assertEquals(PaymentStatus.PAID, paid.getStatus());
    }

    // ───────────── thoi diem da tra tien ─────────────

    @Test
    void thePaidAtTimestampIsReadableForTheOrder() {
        OrderEntity order = payableOrder();
        PaymentEntity paid = attempt(PaymentStatus.PAID, 1, order);
        Instant paidAt = Instant.now().minus(2, ChronoUnit.MINUTES);
        paid.setPaidAt(paidAt);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(paid));

        assertEquals(paidAt, service.findPaidAtForOrder(order.getId()));
    }

    // ───────────── yeu cau hoan tien (CK-4a) ─────────────

    /** Don da tra tien bi huy: phai co dung MOT khoan hoan, ly do ORDER_CANCELLED. */
    @Test
    void cancellingAPaidOrderRecordsARefund() {
        OrderEntity order = payableOrder();
        PaymentEntity paid = attempt(PaymentStatus.PAID, 1, order);
        paid.setAmount(new BigDecimal("1030000"));
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(paid));
        when(refunds.existsByOrderId(order.getId())).thenReturn(false);
        when(refunds.save(any(OrderRefundEntity.class))).thenAnswer(call -> call.getArgument(0));

        service.requestRefundIfPaid(order, RefundReason.ORDER_CANCELLED);

        ArgumentCaptor<OrderRefundEntity> captor = ArgumentCaptor.forClass(OrderRefundEntity.class);
        verify(refunds).save(captor.capture());
        OrderRefundEntity saved = captor.getValue();

        assertEquals(RefundReason.ORDER_CANCELLED, saved.getReason());
        assertEquals(RefundStatus.PENDING, saved.getStatus());
        assertEquals(paid, saved.getPayment());
        assertEquals(order, saved.getOrder());
    }

    /**
     * so tien hoan lay tu payments.amount, KHONG phai orders.total.
     *
     * Don trong fixture co total = 1.030.000. Cho payments.amount khac di (990.000) de neu code
     * lay nham tu don thi test do ngay — neu ca hai bang nhau thi test nay vo dung.
     */
    @Test
    void theRefundAmountComesFromThePaymentNotTheOrder() {
        OrderEntity order = payableOrder();               // order.getTotal() = 1.030.000
        PaymentEntity paid = attempt(PaymentStatus.PAID, 1, order);
        paid.setAmount(new BigDecimal("990000"));         // so tien THUC SU thu duoc
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(paid));
        when(refunds.existsByOrderId(order.getId())).thenReturn(false);
        when(refunds.save(any(OrderRefundEntity.class))).thenAnswer(call -> call.getArgument(0));

        service.requestRefundIfPaid(order, RefundReason.ORDER_CANCELLED);

        ArgumentCaptor<OrderRefundEntity> captor = ArgumentCaptor.forClass(OrderRefundEntity.class);
        verify(refunds).save(captor.capture());
        assertEquals(new BigDecimal("990000"), captor.getValue().getAmount());
    }

    /** Huy don CHUA thanh toan: khong co khoan hoan nao. Day la ca thuong gap nhat. */
    @Test
    void cancellingAnUnpaidOrderRecordsNoRefund() {
        OrderEntity order = payableOrder();
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(attempt(PaymentStatus.PENDING, 1, order)));

        service.requestRefundIfPaid(order, RefundReason.ORDER_CANCELLED);

        verify(refunds, never()).save(any());
    }

    /** Don khong co ban ghi payment nao (du lieu cu): khong nem, khong ghi. */
    @Test
    void anOrderWithoutAnyPaymentRecordsNoRefund() {
        OrderEntity order = payableOrder();
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.empty());

        service.requestRefundIfPaid(order, RefundReason.ORDER_CANCELLED);

        verify(refunds, never()).save(any());
    }

    /**
     * Goi lai lan hai phai IM LANG bo qua, khong nem.
     *
     * Bon duong cung co the goi ham nay cho mot don: khach huy, shop huy, IPN ve muon, IPN gui
     * lap. Nguoi goi khong lam gi sai — chi la su kien den lan thu hai.
     */
    @Test
    void aSecondRefundRequestIsSilentlyIgnored() {
        OrderEntity order = payableOrder();
        PaymentEntity paid = attempt(PaymentStatus.PAID, 1, order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(paid));
        when(refunds.existsByOrderId(order.getId())).thenReturn(true);   // da co roi

        service.requestRefundIfPaid(order, RefundReason.ORDER_CANCELLED);

        verify(refunds, never()).save(any());
    }

    /** Ly do phai duoc ghi dung — day la thu phan biet "khach huy" voi "IPN ve muon". */
    @Test
    void theReasonDistinguishesPaidAfterCancelFromOrderCancelled() {
        OrderEntity order = payableOrder();
        PaymentEntity paid = attempt(PaymentStatus.PAID, 1, order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(paid));
        when(refunds.existsByOrderId(order.getId())).thenReturn(false);
        when(refunds.save(any(OrderRefundEntity.class))).thenAnswer(call -> call.getArgument(0));

        service.requestRefundIfPaid(order, RefundReason.PAID_AFTER_CANCEL);

        ArgumentCaptor<OrderRefundEntity> captor = ArgumentCaptor.forClass(OrderRefundEntity.class);
        verify(refunds).save(captor.capture());
        assertEquals(RefundReason.PAID_AFTER_CANCEL, captor.getValue().getReason());
    }

    /** payment FAILED / EXPIRED / CANCELLED: khong co tien vao thi khong hoan. */
    @Test
    void aClosedButUnpaidPaymentNeverProducesARefund() {
        for (PaymentStatus status : new PaymentStatus[]{
                PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED}) {
            OrderEntity order = payableOrder();
            when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                    .thenReturn(Optional.of(attempt(status, 1, order)));

            service.requestRefundIfPaid(order, RefundReason.ORDER_CANCELLED);
        }
        verify(refunds, never()).save(any());
    }

    /**
     * findOrderForConfirmation phai di qua finder CO KHOA.
     *
     * Test nay ton tai de chan mot "toi uu" rat de xay ra: doi findForUpdateWithItemsById thanh
     * findById cho gon. Mat khoa la mo lai khe tranh chap giua IPN va tac vu quet het han.
     */
    @Test
    void findOrderForConfirmationUsesTheLockingFinder() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = payableOrder();
        when(orders.findForUpdateWithItemsById(orderId)).thenReturn(Optional.of(order));

        assertEquals(Optional.of(order), service.findOrderForConfirmation(orderId));
        verify(orders).findForUpdateWithItemsById(orderId);
        verify(orders, never()).findById(any());
    }

    @Test
    void findOrderForConfirmationReturnsEmptyForANullIdAndNeverHitsTheDatabase() {
        assertTrue(service.findOrderForConfirmation(null).isEmpty());
        verify(orders, never()).findForUpdateWithItemsById(any());
    }

    @Test
    void anUnpaidOrderHasNoPaidAtAndAnOrderWithoutPaymentNeither() {
        OrderEntity paidOrder = payableOrder();
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(paidOrder.getId()))
                .thenReturn(Optional.of(attempt(PaymentStatus.PENDING, 1, paidOrder)));
        assertNull(service.findPaidAtForOrder(paidOrder.getId()));

        OrderEntity noPaymentOrder = payableOrder();
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(noPaymentOrder.getId()))
                .thenReturn(Optional.empty());
        assertNull(service.findPaidAtForOrder(noPaymentOrder.getId()));
    }

    // ───────────── CK-7b: shop xac nhan thu tien mat (COD) ─────────────

    /** COD order dang SHIPPING, voi mot lan thu PENDING. */
    private OrderEntity codOrder(OrderStatus status) {
        return order(PaymentMethod.COD, status, null);
    }

    private void stubCodOrder(OrderEntity order) {
        when(orders.findForUpdateByOrderNumber(ORDER_NO)).thenReturn(Optional.of(order));
    }

    /**
     * Ca chinh: don COD dang SHIPPING, payment PENDING -> PAID, paidAt = bay gio,
     * va TRANG THAI DON KHONG DOI.
     */
    @Test
    void confirmingCodMarksThePaymentPaidWithoutTouchingTheOrderStatus() {
        OrderEntity order = codOrder(OrderStatus.SHIPPING);
        PaymentEntity pending = attempt(PaymentStatus.PENDING, 1, order);
        pending.setPaidAt(null);
        stubCodOrder(order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(pending));

        Instant before = Instant.now();
        PaymentResponse response = service.confirmCodPayment(ORDER_NO);

        assertEquals(PaymentStatus.PAID, pending.getStatus());
        assertNotNull(pending.getPaidAt());
        assertTrue(!pending.getPaidAt().isBefore(before));
        assertEquals(PaymentStatus.PAID, response.status());
        // Thu tien mat KHONG xac nhan/hoan tat don.
        assertEquals(OrderStatus.SHIPPING, order.getStatus());
    }

    /** Don COD da COMPLETED van thu duoc tien (giao xong moi thu). */
    @Test
    void confirmingCodWorksOnACompletedOrder() {
        OrderEntity order = codOrder(OrderStatus.COMPLETED);
        PaymentEntity pending = attempt(PaymentStatus.PENDING, 1, order);
        stubCodOrder(order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(pending));

        service.confirmCodPayment(ORDER_NO);

        assertEquals(PaymentStatus.PAID, pending.getStatus());
    }

    /**
     * Idempotent: goi lai tren payment da PAID tra ket qua hien tai va KHONG ghi de paidAt.
     * Day la diem de hong nhat — bam hai lan khong duoc doi moc da thu.
     */
    @Test
    void confirmingAnAlreadyPaidCodIsIdempotentAndKeepsTheOriginalPaidAt() {
        OrderEntity order = codOrder(OrderStatus.COMPLETED);
        PaymentEntity paid = attempt(PaymentStatus.PAID, 1, order);
        Instant originalPaidAt = Instant.now().minus(3, ChronoUnit.HOURS);
        paid.setPaidAt(originalPaidAt);
        stubCodOrder(order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.of(paid));

        PaymentResponse response = service.confirmCodPayment(ORDER_NO);

        assertEquals(PaymentStatus.PAID, response.status());
        // Moc cu phai nguyen ven.
        assertEquals(originalPaidAt, paid.getPaidAt());
    }

    /** Don chuyen khoan KHONG di qua cua nay: phai dung luong VNPay. */
    @Test
    void confirmingCodRejectsABankTransferOrder() {
        OrderEntity order = order(PaymentMethod.BANK_TRANSFER, OrderStatus.SHIPPING, null);
        stubCodOrder(order);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.confirmCodPayment(ORDER_NO));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    /** Don da huy: tu choi voi thong diep rieng ve "cancelled". */
    @Test
    void confirmingCodRejectsACancelledOrder() {
        OrderEntity order = codOrder(OrderStatus.CANCELLED);
        stubCodOrder(order);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.confirmCodPayment(ORDER_NO));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("cancelled"));
    }

    /** Don chua giao (PENDING / CONFIRMED): chua co gi de thu. */
    @Test
    void confirmingCodRejectsAnOrderThatHasNotShippedYet() {
        for (OrderStatus status : new OrderStatus[]{OrderStatus.PENDING, OrderStatus.CONFIRMED}) {
            OrderEntity order = codOrder(status);
            when(orders.findForUpdateByOrderNumber(ORDER_NO)).thenReturn(Optional.of(order));

            ApiException exception = assertThrows(ApiException.class,
                    () -> service.confirmCodPayment(ORDER_NO));

            assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        }
    }

    /** Don khong ton tai: 404. */
    @Test
    void confirmingCodForAMissingOrderIsNotFound() {
        when(orders.findForUpdateByOrderNumber(ORDER_NO)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.confirmCodPayment(ORDER_NO)).getErrorCode());
    }

    /** Don khong co ban ghi payment (du lieu tao truoc CK-2): 404, khong NPE. */
    @Test
    void confirmingCodForAnOrderWithoutPaymentIsNotFound() {
        OrderEntity order = codOrder(OrderStatus.SHIPPING);
        stubCodOrder(order);
        when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                .thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.confirmCodPayment(ORDER_NO)).getErrorCode());
    }

    /** Payment da dong nhung CHUA thu tien (CANCELLED/EXPIRED/FAILED): khong duoc am tham ghi PAID. */
    @Test
    void confirmingCodRejectsAClosedButUnpaidPayment() {
        for (PaymentStatus status : new PaymentStatus[]{
                PaymentStatus.CANCELLED, PaymentStatus.EXPIRED, PaymentStatus.FAILED}) {
            OrderEntity order = codOrder(OrderStatus.SHIPPING);
            when(orders.findForUpdateByOrderNumber(ORDER_NO)).thenReturn(Optional.of(order));
            when(payments.findFirstByOrderIdOrderByAttemptNoDesc(order.getId()))
                    .thenReturn(Optional.of(attempt(status, 1, order)));

            ApiException exception = assertThrows(ApiException.class,
                    () -> service.confirmCodPayment(ORDER_NO));

            assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        }
    }

    // ───────────── CK-7a: danh sach hoan tien cho shop ─────────────

    /** Dung projection lam du lieu test — khong can entity that. */
    private RefundAdminView refundView(String orderNo, String customerName,
                                       BigDecimal amount, RefundReason reason, RefundStatus status) {
        RefundAdminView.OrderRef orderRef = new RefundAdminView.OrderRef() {
            public String getOrderNumber() { return orderNo; }
            public String getReceiverName() { return customerName; }
        };
        return new RefundAdminView() {
            public UUID getId() { return UUID.randomUUID(); }
            public BigDecimal getAmount() { return amount; }
            public RefundReason getReason() { return reason; }
            public RefundStatus getStatus() { return status; }
            public Instant getCreatedAt() { return Instant.now(); }
            public OrderRef getOrder() { return orderRef; }
        };
    }

    /** Khong loc trang thai: lay TAT CA (findByCreatedAtNotNull), khong goi findByStatus. */
    @Test
    void listingRefundsWithoutAStatusReturnsAll() {
        Pageable pageable = PageRequest.of(0, 20);
        RefundAdminView view = refundView(ORDER_NO, "Nguyen Van A",
                new BigDecimal("1030000"), RefundReason.ORDER_CANCELLED, RefundStatus.PENDING);
        when(refunds.findByCreatedAtNotNull(pageable))
                .thenReturn(new PageImpl<>(List.of(view), pageable, 1));

        PageResponse<RefundAdminResponse> page = service.getRefunds(null, pageable);

        assertEquals(1, page.content().size());
        verify(refunds).findByCreatedAtNotNull(pageable);
        verify(refunds, never()).findByStatus(any(), any());
    }

    /** Co loc: goi findByStatus voi dung trang thai, khong goi nhanh lay tat ca. */
    @Test
    void listingRefundsFiltersByStatusWhenProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(refunds.findByStatus(RefundStatus.REFUNDED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getRefunds(RefundStatus.REFUNDED, pageable);

        verify(refunds).findByStatus(RefundStatus.REFUNDED, pageable);
        verify(refunds, never()).findByCreatedAtNotNull(any());
    }

    /**
     * Trai phang dung: orderNumber va customerName khong duoc hoan doi cho nhau.
     *
     * Cho hai gia tri khac han nhau de neu code gan nham (lay receiverName cho orderNumber) thi
     * test do ngay.
     */
    @Test
    void theRefundRowFlattensOrderNumberAndCustomerNameWithoutSwapping() {
        Pageable pageable = PageRequest.of(0, 20);
        RefundAdminView view = refundView("LBVN-ORDER-9", "Tran Thi B",
                new BigDecimal("500000"), RefundReason.PAID_AFTER_CANCEL, RefundStatus.PENDING);
        when(refunds.findByCreatedAtNotNull(pageable))
                .thenReturn(new PageImpl<>(List.of(view), pageable, 1));

        RefundAdminResponse row = service.getRefunds(null, pageable).content().get(0);

        assertEquals("LBVN-ORDER-9", row.orderNumber());
        assertEquals("Tran Thi B", row.customerName());
        assertEquals(new BigDecimal("500000"), row.amount());
        assertEquals(RefundReason.PAID_AFTER_CANCEL, row.reason());
        assertEquals(RefundStatus.PENDING, row.status());
    }
}
