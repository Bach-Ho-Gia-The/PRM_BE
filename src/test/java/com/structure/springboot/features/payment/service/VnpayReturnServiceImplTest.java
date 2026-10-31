package com.structure.springboot.features.payment.service;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.payment.dto.response.VnpayReturnResponse;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.enums.VnpayReturnState;
import com.structure.springboot.features.payment.repository.PaymentRepository;
import com.structure.springboot.features.payment.service.impl.VnpayReturnServiceImpl;
import com.structure.springboot.infrastructure.external.vnpay.VnpayProperties;
import com.structure.springboot.infrastructure.external.vnpay.VnpaySignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Trang Return — thu khach nhin thay ngay sau khi tra tien.
 *
 * <p>Nguyen tac xuyen suot: trang nay CHI DOC. Moi test deu duoc viet de bat mot cach hieu sai
 * cu the, khong phai de tang so luong.</p>
 */
@ExtendWith(MockitoExtension.class)
class VnpayReturnServiceImplTest {

    private static final String HASH_SECRET = "DEMOHASHESECRET0004";
    private static final String TXN_REF = "b1c2d3e4f5061728394a5b6c7d8e9f01";
    private static final BigDecimal AMOUNT = new BigDecimal("1030000.00");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private PaymentRepository payments;

    private VnpayReturnServiceImpl service;

    private OrderEntity order;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        VnpayProperties properties = new VnpayProperties(
                "DEMOV210", HASH_SECRET,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                "http://localhost:8088/api/v1/vnpay/return",
                "2.1.0", "pay", "VND", "vn", "other", VN);
        service = new VnpayReturnServiceImpl(payments, properties);

        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());

        order = OrderEntity.builder()
                .orderNumber("LBVN-2610-RTN1").account(account)
                .status(OrderStatus.PENDING).total(new BigDecimal("1030000.00")).totalItems(1)
                .build();
        order.setId(UUID.randomUUID());

        payment = PaymentEntity.builder()
                .order(order).txnRef(TXN_REF).amount(AMOUNT)
                .status(PaymentStatus.PAID).attemptNo(1).build();
        payment.setId(UUID.randomUUID());
    }

    private Map<String, String> params(String responseCode) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("vnp_Amount", "103000000");
        parameters.put("vnp_BankCode", "NCB");
        parameters.put("vnp_OrderInfo", "Thanh toan " + TXN_REF);
        parameters.put("vnp_PayDate", "20261028143210");
        parameters.put("vnp_ResponseCode", responseCode);
        parameters.put("vnp_TmnCode", "DEMOV210");
        parameters.put("vnp_TransactionNo", "14226112");
        parameters.put("vnp_TxnRef", TXN_REF);
        parameters.put("vnp_SecureHash",
                VnpaySignature.sign(VnpaySignature.canonicalQuery(parameters), HASH_SECRET));
        return parameters;
    }

    // ══════════════ Chu ky ══════════════

    /**
     * Chu ky sai ⇒ bao "lien ket khong hop le" VA khong doc database.
     *
     * Khong doc DB la phan quan trong: neu doc, ta se hien thi thong tin don cua mot nguoi cho bat
     * ky ai tu dung URL — va txnRef la thu co the doan duoc.
     */
    @Test
    void anInvalidSignatureShowsInvalidLinkWithoutReadingTheDatabase() {
        Map<String, String> parameters = params("00");
        parameters.put("vnp_SecureHash", "0".repeat(128));

        VnpayReturnResponse view = service.buildReturnView(parameters);

        assertEquals(VnpayReturnState.INVALID_LINK, view.state());
        assertNull(view.orderNumber());
        assertNull(view.amountText());
        verifyNoInteractions(payments);
    }

    @Test
    void aMissingSignatureShowsInvalidLink() {
        Map<String, String> parameters = params("00");
        parameters.remove("vnp_SecureHash");

        assertEquals(VnpayReturnState.INVALID_LINK, service.buildReturnView(parameters).state());
        verifyNoInteractions(payments);
    }

    // ══════════════ Trang thai lay tu DATABASE ══════════════

    /** Da PAID, don con song ⇒ thanh cong, co so tien va gio thanh toan. */
    @Test
    void aPaidPaymentShowsSuccessWithAmountAndPaidTime() {
        payment.setPaidAt(Instant.parse("2026-10-28T07:32:10Z"));
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));

        VnpayReturnResponse view = service.buildReturnView(params("00"));

        assertEquals(VnpayReturnState.SUCCESS, view.state());
        assertEquals("LBVN-2610-RTN1", view.orderNumber());
        assertEquals("1.030.000 ₫", view.amountText());
        // 07:32:10Z = 14:32:10 gio Viet Nam
        assertEquals("14:32 ngày 28/10/2026", view.paidAtText());
    }

    /**
     * Diem mau chot: URL bao THAT BAI nhung database bao da tra tien ⇒ hien THANH CONG.
     *
     * Khach co the sua vnp_ResponseCode thanh 24 roi F5. Neu trang tin URL thi khach thay "that
     * bai" cho mot don da tra tien — va lan sau se tra lai lan nua.
     */
    @Test
    void aTamperedFailureCodeOnTheUrlStillShowsSuccessWhenTheDatabaseSaysPaid() {
        payment.setPaidAt(Instant.parse("2026-10-28T07:32:10Z"));
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));

        VnpayReturnResponse view = service.buildReturnView(params("24"));

        assertEquals(VnpayReturnState.SUCCESS, view.state());
    }

    /**
     * Nguoc lai: URL bao THANH CONG nhung DB van PENDING ⇒ "dang xac nhan", KHONG phai thanh cong
     * va cung KHONG phai that bai.
     *
     * Day la ca xay ra that khi IPN cham. Hien "that bai" o day la moi khach tra tien lan hai.
     */
    @Test
    void aSuccessCodeWithAPendingPaymentAsksTheCustomerToWait() {
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaidAt(null);
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));

        VnpayReturnResponse view = service.buildReturnView(params("00"));

        assertEquals(VnpayReturnState.PENDING_CONFIRMATION, view.state());
        assertNull(view.paidAtText());
        assertTrue(view.toHtml().contains("không thanh toán lại"),
                "phai noi ro dung tra lai");
    }

    /** URL bao that bai va DB cung PENDING ⇒ that bai. */
    @Test
    void aFailureCodeWithAPendingPaymentShowsFailure() {
        payment.setStatus(PaymentStatus.PENDING);
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));

        assertEquals(VnpayReturnState.FAILED, service.buildReturnView(params("24")).state());
    }

    /** Don da HUY ma tien ve (L16): noi se hoan tien, khong noi "thanh cong". */
    @Test
    void aPaidPaymentForACancelledOrderSaysRefundIsComing() {
        payment.setPaidAt(Instant.parse("2026-10-28T07:32:10Z"));
        order.setStatus(OrderStatus.CANCELLED);
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));

        VnpayReturnResponse view = service.buildReturnView(params("00"));

        assertEquals(VnpayReturnState.PAID_AFTER_CANCEL, view.state());
        assertTrue(view.toHtml().contains("hoàn lại"), "phai noi ro se hoan tien");
    }

    /** Payment da dong (FAILED/EXPIRED/CANCELLED) ⇒ that bai, du URL noi gi. */
    @Test
    void closedPaymentsShowFailure() {
        for (PaymentStatus status : new PaymentStatus[]{
                PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED}) {
            payment.setStatus(status);
            when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.of(payment));

            assertEquals(VnpayReturnState.FAILED,
                    service.buildReturnView(params("00")).state());
        }
    }

    @Test
    void anUnknownTxnRefShowsNotFound() {
        when(payments.findByTxnRef(TXN_REF)).thenReturn(Optional.empty());

        VnpayReturnResponse view = service.buildReturnView(params("00"));

        assertEquals(VnpayReturnState.TXN_REF_NOT_FOUND, view.state());
        assertNull(view.orderNumber());
    }

    // ══════════════ HTML ══════════════

    /**
     * XSS: du lieu tu ngoai phai duoc escape.
     *
     * Khong co Thymeleaf thi khong co auto-escape. Thieu buoc nay la trang thanh toan cua minh
     * thanh noi chay ma cua nguoi khac.
     */
    @Test
    void hostileValuesAreEscapedInTheRenderedHtml() {
        VnpayReturnResponse view = new VnpayReturnResponse(
                VnpayReturnState.SUCCESS,
                "<script>alert('x')</script>",
                "1.030.000 ₫",
                null,
                "\"><img src=x onerror=alert(1)>",
                "00");

        String html = view.toHtml();

        assertFalse(html.contains("<script>alert"), "the script khong duoc lot vao HTML");
        assertFalse(html.contains("<img src=x"), "thuoc tinh la khong duoc lot vao HTML");
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&quot;&gt;&lt;img"));
    }

    /** Trang phai la mot tai lieu HTML hoan chinh, va khong duoc trong. */
    @Test
    void everyStateRendersACompleteHtmlDocument() {
        for (VnpayReturnState state : VnpayReturnState.values()) {
            String html = new VnpayReturnResponse(state, "LBVN-1", "1.000 ₫", null, "ref", "00").toHtml();

            assertTrue(html.startsWith("<!DOCTYPE html>"), "thieu doctype o " + state);
            assertTrue(html.contains("</html>"), "thieu the dong o " + state);
            assertTrue(html.length() > 500, "trang qua ngan o " + state);
            // Khong duoc con placeholder chua duoc thay.
            assertFalse(html.contains("%s"), "con placeholder o " + state);
        }
    }

    /** Khong co du lieu thi khong in dong trong — dung im lang con hon in "(trong)". */
    @Test
    void emptyFieldsAreOmittedRatherThanPrintedBlank() {
        String html = VnpayReturnResponse.txnRefNotFound(TXN_REF).toHtml();

        assertTrue(html.contains(TXN_REF));
        assertFalse(html.contains("Mã đơn"), "khong co ma don thi khong in nhan");
        assertFalse(html.contains("Số tiền"), "khong co so tien thi khong in nhan");
    }
}
