package com.structure.springboot.features.payment.service;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.payment.dto.response.VnpayIpnResponse;
import com.structure.springboot.features.payment.entity.OrderRefundEntity;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.enums.RefundStatus;
import com.structure.springboot.features.payment.repository.OrderRefundRepository;
import com.structure.springboot.features.payment.repository.PaymentRepository;
import com.structure.springboot.features.payment.service.impl.VnpayCallbackServiceImpl;
import com.structure.springboot.infrastructure.external.vnpay.VnpayProperties;
import com.structure.springboot.infrastructure.external.vnpay.VnpaySignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IPN cua VNPay — nguon xac nhan thanh toan DUY NHAT.
 *
 * <p>Moi test o day tuong ung mot dong trong bang quyet dinh 9 to hop. Neu bang do thay doi thi
 * file nay phai doi theo — do la chu y, khong phai trung lap.</p>
 *
 * <p>Nhan test de ASCII thuan vi output surefire ma hoa hong tieng Viet co dau.</p>
 */
@ExtendWith(MockitoExtension.class)
class VnpayCallbackServiceImplTest {

    private static final String HASH_SECRET = "DEMOHASHESECRET0004";
    private static final String TXN_REF = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private static final BigDecimal AMOUNT = new BigDecimal("1030000.00");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private PaymentRepository payments;
    @Mock private OrderRefundRepository refunds;
    @Mock private PaymentService paymentService;

    private VnpayCallbackServiceImpl service;
    private VnpayProperties properties;

    private UUID orderId;
    private OrderEntity order;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        properties = new VnpayProperties(
                "DEMOV210", HASH_SECRET,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                "http://localhost:8088/api/v1/vnpay/return",
                "2.1.0", "pay", "VND", "vn", "other", VN);

        service = new VnpayCallbackServiceImpl(payments, refunds, paymentService, properties);

        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());

        order = OrderEntity.builder()
                .orderNumber("LBVN-2610-IPN1").account(account)
                .status(OrderStatus.PENDING).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .subtotal(new BigDecimal("1000000.00")).shippingFee(new BigDecimal("30000.00"))
                .discount(BigDecimal.ZERO).total(new BigDecimal("1030000.00")).totalItems(1)
                .build();
        orderId = UUID.randomUUID();
        order.setId(orderId);

        payment = PaymentEntity.builder()
                .order(order).txnRef(TXN_REF).amount(AMOUNT)
                .status(PaymentStatus.PENDING).attemptNo(1).build();
        payment.setId(UUID.randomUUID());
    }

    /** Dung tham so IPN that, KY that bang chinh VnpaySignature — nho vay test di qua duoc buoc 1. */
    private Map<String, String> ipn(String responseCode) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("vnp_Amount", "103000000");     // da nhan 100
        parameters.put("vnp_BankCode", "NCB");
        parameters.put("vnp_BankTranNo", "VNP14226112");
        parameters.put("vnp_CardType", "ATM");
        parameters.put("vnp_OrderInfo", "Thanh toan " + TXN_REF);
        parameters.put("vnp_PayDate", "20261028143210");
        parameters.put("vnp_ResponseCode", responseCode);
        parameters.put("vnp_TmnCode", "DEMOV210");
        parameters.put("vnp_TransactionNo", "14226112");
        parameters.put("vnp_TransactionStatus", responseCode);
        parameters.put("vnp_TxnRef", TXN_REF);
        parameters.put("vnp_SecureHash",
                VnpaySignature.sign(VnpaySignature.canonicalQuery(parameters), HASH_SECRET));
        return parameters;
    }

    private void stubFoundWith(PaymentStatus status, OrderStatus orderStatus) {
        payment.setStatus(status);
        order.setStatus(orderStatus);
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));
        when(paymentService.findOrderForConfirmation(orderId)).thenReturn(Optional.of(order));
    }

    // ══════════════ Tang 1: chu ky va tham so ══════════════

    /** Chu ky sai: dung ngay, KHONG duoc doc database. */
    @Test
    void anInvalidSignatureIsRejectedBeforeTouchingTheDatabase() {
        Map<String, String> parameters = ipn("00");
        parameters.put("vnp_SecureHash", "0".repeat(128));

        assertEquals(VnpayIpnResponse.RSP_INVALID_SIGNATURE, service.handleIpn(parameters).rspCode());

        verifyNoInteractions(payments);
        verifyNoInteractions(refunds);
    }

    @Test
    void aMissingSignatureIsRejected() {
        Map<String, String> parameters = ipn("00");
        parameters.remove("vnp_SecureHash");

        assertEquals(VnpayIpnResponse.RSP_INVALID_SIGNATURE, service.handleIpn(parameters).rspCode());
        verifyNoInteractions(payments);
    }

    /** txnRef la: tra 01 de VNPay goi lai (co the don chua kip commit o phia minh). */
    @Test
    void anUnknownTxnRefReturnsOrderNotFound() {
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.empty());

        assertEquals(VnpayIpnResponse.RSP_ORDER_NOT_FOUND, service.handleIpn(ipn("00")).rspCode());
    }

    /**
     * So tien lech: tra 04.
     *
     * Ca nay kho phat hien neu code quen chia 100: vnp_Amount=103000000 ma amount=1030000.00, so
     * truc tiep la lech, va MOI IPN se bi tu choi — nhin tu ngoai chi thay "khong xac nhan duoc".
     */
    @Test
    void aAmountMismatchReturnsInvalidAmount() {
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));
        Map<String, String> parameters = ipn("00");
        parameters.put("vnp_Amount", "1");
        parameters.put("vnp_SecureHash",
                VnpaySignature.sign(VnpaySignature.canonicalQuery(parameters), HASH_SECRET));

        assertEquals(VnpayIpnResponse.RSP_INVALID_AMOUNT, service.handleIpn(parameters).rspCode());
        verify(paymentService, never()).findOrderForConfirmation(any());
    }

    /** So tien dung nhung scale khac (1030000 vs 1030000.00) van phai khop. */
    @Test
    void theAmountComparisonIgnoresScale() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.PENDING);

        assertEquals(VnpayIpnResponse.RSP_SUCCESS, service.handleIpn(ipn("00")).rspCode());
    }

    // ══════════════ Tang 2: bang quyet dinh ══════════════

    /** Dong 1: 00 + PENDING + don PENDING -> PAID, don GIU PENDING (luat 4). */
    @Test
    void aSuccessfulPaymentIsConfirmedAndTheOrderStaysPending() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.PENDING);

        VnpayIpnResponse outcome = service.handleIpn(ipn("00"));

        assertEquals(VnpayIpnResponse.RSP_SUCCESS, outcome.rspCode());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertNotNull(payment.getPaidAt());
        assertEquals("14226112", payment.getGatewayTransactionNo());
        assertEquals("NCB", payment.getBankCode());
        assertEquals("00", payment.getResponseCode());
        // Luat 4: thanh toan xong don VAN PENDING cho shop xac nhan.
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    /**
     * paid_at lay tu vnp_PayDate, KHONG phai now().
     *
     * 20261028143210 gio VN = 14:32:10 ngay 28/10/2026 = 07:32:10Z.
     */
    @Test
    void paidAtComesFromTheGatewayTimestampNotFromNow() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.PENDING);

        service.handleIpn(ipn("00"));

        assertEquals(Instant.parse("2026-10-28T07:32:10Z"), payment.getPaidAt());
    }

    /** vnp_PayDate hong: van ghi nhan thanh toan, lui ve now(). Khong tu choi IPN. */
    @Test
    void anUnparseablePayDateStillConfirmsThePayment() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.PENDING);
        Map<String, String> parameters = ipn("00");
        parameters.put("vnp_PayDate", "khong-phai-ngay");
        parameters.put("vnp_SecureHash",
                VnpaySignature.sign(VnpaySignature.canonicalQuery(parameters), HASH_SECRET));
        Instant before = Instant.now().minusSeconds(5);

        service.handleIpn(parameters);

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertTrue(payment.getPaidAt().isAfter(before));
    }

    /** Dong 2: 00 + da PAID roi -> idempotent, tra 02 de VNPay ngung goi lai. */
    @Test
    void aDuplicateSuccessIsIdempotentAndReturnsAlreadyConfirmed() {
        stubFoundWith(PaymentStatus.PAID, OrderStatus.PENDING);
        Instant originalPaidAt = Instant.now().minus(10, ChronoUnit.MINUTES);
        payment.setPaidAt(originalPaidAt);

        VnpayIpnResponse outcome = service.handleIpn(ipn("00"));

        assertEquals(VnpayIpnResponse.RSP_ALREADY_CONFIRMED, outcome.rspCode());
        assertEquals(originalPaidAt, payment.getPaidAt(), "paid_at KHONG duoc ghi de");
        verify(refunds, never()).save(any());
    }

    /**
     * Dong 3 — L16: 00 + don da CANCELLED -> payment PAID + ghi khoan hoan, tra 00.
     *
     * Day la ca kho nhat: khach da tra tien, don da chet, hang da ve kho. Tien PHAI duoc ghi nhan
     * du don khong con — khong ghi la mat tien cua khach ma khong ai biet.
     */
    @Test
    void aPaymentForACancelledOrderIsRecordedWithARefund() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.CANCELLED);
        when(refunds.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(refunds.save(any(OrderRefundEntity.class))).thenAnswer(call -> call.getArgument(0));

        VnpayIpnResponse outcome = service.handleIpn(ipn("00"));

        assertEquals(VnpayIpnResponse.RSP_SUCCESS, outcome.rspCode());
        assertNotNull(outcome.refundRequested(), "phai bao ra khoan hoan vua ghi");
        assertEquals(AMOUNT, outcome.refundRequested().amount());
        assertEquals(RefundReason.PAID_AFTER_CANCEL, outcome.refundRequested().reason());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        // Don GIU CANCELLED — khong hoi sinh don da huy.
        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        ArgumentCaptor<OrderRefundEntity> captor = ArgumentCaptor.forClass(OrderRefundEntity.class);
        verify(refunds).save(captor.capture());
        OrderRefundEntity refund = captor.getValue();
        assertEquals(RefundReason.PAID_AFTER_CANCEL, refund.getReason());
        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertEquals(AMOUNT, refund.getAmount());
    }

    /** L16 nhung khoan hoan da co san (IPN lap) -> khong ghi them, van tra 00. */
    @Test
    void aDuplicateLatePaymentDoesNotCreateASecondRefund() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.CANCELLED);
        when(refunds.findByOrderId(orderId))
                .thenReturn(Optional.of(OrderRefundEntity.builder()
                        .order(order).payment(payment).amount(AMOUNT)
                        .reason(RefundReason.PAID_AFTER_CANCEL).build()));

        assertEquals(VnpayIpnResponse.RSP_SUCCESS, service.handleIpn(ipn("00")).rspCode());
        verify(refunds, never()).save(any());
    }

    /**
     * Dong 5: ma loi + PENDING + don PENDING -> payment FAILED, don GIU PENDING.
     *
     * Don khong bi huy: khach dong app giua luc nhap OTP van con thoi gian thu lai. Huy o day la
     * tuoc quyen thu lai cua khach vi mot lan bam nham.
     */
    @Test
    void aFailedPaymentMarksOnlyThePaymentAndLeavesTheOrderPending() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.PENDING);

        VnpayIpnResponse outcome = service.handleIpn(ipn("24"));

        assertEquals(VnpayIpnResponse.RSP_SUCCESS, outcome.rspCode());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("24", payment.getResponseCode());
        assertEquals(OrderStatus.PENDING, order.getStatus(), "don PHAI giu PENDING");
        assertNull(payment.getPaidAt());
    }

    /** Dong 6: ma loi + da PAID -> KHONG lui trang thai. Tien da ve thi khong xoa dau vet. */
    @Test
    void aFailureAfterSuccessNeverRevertsAPaidPayment() {
        stubFoundWith(PaymentStatus.PAID, OrderStatus.PENDING);

        assertEquals(VnpayIpnResponse.RSP_ALREADY_CONFIRMED, service.handleIpn(ipn("24")).rspCode());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
    }

    /** Dong 7: ma loi + da FAILED -> idempotent. */
    @Test
    void aDuplicateFailureIsIdempotent() {
        stubFoundWith(PaymentStatus.FAILED, OrderStatus.PENDING);

        assertEquals(VnpayIpnResponse.RSP_ALREADY_CONFIRMED, service.handleIpn(ipn("24")).rspCode());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    /** payment da EXPIRED ma IPN bao thanh cong: ghi nhan + hoan tien, giong L16. */
    @Test
    void aSuccessForAnExpiredPaymentIsAlsoRecordedWithARefund() {
        stubFoundWith(PaymentStatus.EXPIRED, OrderStatus.CANCELLED);
        when(refunds.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(refunds.save(any(OrderRefundEntity.class))).thenAnswer(call -> call.getArgument(0));

        VnpayIpnResponse outcome = service.handleIpn(ipn("00"));

        assertEquals(VnpayIpnResponse.RSP_SUCCESS, outcome.rspCode());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(PaymentStatus.EXPIRED, outcome.previousStatus(),
                "previousStatus phai la trang thai TRUOC khi xu ly");
        verify(refunds).save(any(OrderRefundEntity.class));
    }

    // ══════════════ Tang 3: rang buoc khoa ══════════════

    /**
     * IPN PHAI goi finder CO KHOA, khong phai finder thuong.
     *
     * Neu ai doi sang findById, test nay do. Do la ca song con: khong khoa thi tac vu quet het han
     * co the huy don ngay sau khi IPN vua xac nhan da tra tien.
     */
    @Test
    void theOrderIsAlwaysReadWithARowLock() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.PENDING);

        service.handleIpn(ipn("00"));

        verify(paymentService).findOrderForConfirmation(orderId);
    }

    /** Don khong ton tai du payment co: tra 01, khong nem. */
    @Test
    void aPaymentPointingToAMissingOrderReturnsOrderNotFound() {
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));
        when(paymentService.findOrderForConfirmation(orderId)).thenReturn(Optional.empty());

        assertEquals(VnpayIpnResponse.RSP_ORDER_NOT_FOUND, service.handleIpn(ipn("00")).rspCode());
    }

    /** IPN thanh cong khong duoc goi requestRefundIfPaid (da tu ghi refund trong transaction nay). */
    @Test
    void theServiceNeverDelegatesTheRefundToAFreshRead() {
        stubFoundWith(PaymentStatus.PENDING, OrderStatus.PENDING);

        service.handleIpn(ipn("00"));

        verify(paymentService, never()).requestRefundIfPaid(any(), any());
    }
}
