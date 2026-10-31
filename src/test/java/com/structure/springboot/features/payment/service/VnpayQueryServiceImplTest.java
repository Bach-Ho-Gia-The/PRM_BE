package com.structure.springboot.features.payment.service;

import com.structure.springboot.features.payment.dto.response.VnpayQueryResult;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.enums.VnpayQueryVerdict;
import com.structure.springboot.features.payment.service.impl.VnpayQueryServiceImpl;
import com.structure.springboot.infrastructure.external.vnpay.VnpayProperties;
import com.structure.springboot.infrastructure.external.vnpay.VnpayQueryClient;
import com.structure.springboot.infrastructure.external.vnpay.VnpaySignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bảng quyết định của {@code querydr}.
 *
 * <p>Không có một lời gọi mạng nào ở đây: client bị mock, body VNPay được dựng và ký tại chỗ bằng
 * {@link VnpaySignature}. Nhờ vậy bảng mã được kiểm đầy đủ, kể cả những mã hiếm khi xảy ra thật.</p>
 */
class VnpayQueryServiceImplTest {

    private static final String HASH_SECRET = "TEST_HASH_SECRET_4F9A2C";
    private static final String TMN_CODE = "6BSNA5CW";
    private static final String TXN_REF = "105df48f66fc4918a91ddd2f5309cf22";
    private static final BigDecimal AMOUNT = new BigDecimal("2988000.00");
    private static final String GATEWAY_AMOUNT = "298800000";

    private VnpayQueryClient client;
    private VnpayQueryServiceImpl service;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        VnpayProperties properties = new VnpayProperties(
                TMN_CODE,
                HASH_SECRET,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                "http://localhost:8088/api/v1/vnpay/return",
                "2.1.0",
                "pay",
                "VND",
                "vn",
                "other",
                ZoneId.of("Asia/Ho_Chi_Minh")
        );

        client = mock(VnpayQueryClient.class);
        service = new VnpayQueryServiceImpl(
                client, properties, new tools.jackson.databind.ObjectMapper());

        payment = PaymentEntity.builder()
                .txnRef(TXN_REF)
                .amount(AMOUNT)
                .status(PaymentStatus.PENDING)
                .createdAt(Instant.parse("2026-10-28T16:44:53Z"))
                .build();
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, String> response(String responseCode, String transactionStatus) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("vnp_ResponseId", "req-1");
        parameters.put("vnp_Command", "querydr");
        parameters.put("vnp_ResponseCode", responseCode);
        parameters.put("vnp_Message", "QueryDR Success");
        parameters.put("vnp_TmnCode", TMN_CODE);
        parameters.put("vnp_TxnRef", TXN_REF);
        parameters.put("vnp_Amount", GATEWAY_AMOUNT);
        parameters.put("vnp_BankCode", "NCB");
        parameters.put("vnp_PayDate", "20261028234453");
        parameters.put("vnp_TransactionNo", "15718028");
        parameters.put("vnp_TransactionType", "01");
        parameters.put("vnp_TransactionStatus", transactionStatus);
        parameters.put("vnp_OrderInfo", "Thanh toan " + TXN_REF);
        parameters.put("vnp_PromotionCode", "");
        parameters.put("vnp_PromotionAmount", "");
        return parameters;
    }

    /** Dựng body JSON đã ký hợp lệ. */
    private String signedBody(Map<String, String> parameters) {
        Map<String, String> copy = new HashMap<>(parameters);
        copy.put("vnp_SecureHash", VnpaySignature.sign(
                com.structure.springboot.infrastructure.external.vnpay
                        .VnpayQuerySignature.responseCanonical(parameters), HASH_SECRET));
        return toJson(copy);
    }

    private String unsignedBody(Map<String, String> parameters) {
        return toJson(new HashMap<>(parameters));
    }

    private String toJson(Map<String, String> parameters) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":\"")
                    .append(entry.getValue().replace("\"", "\\\"")).append('"');
        }
        return json.append('}').toString();
    }

    private VnpayQueryResult query(String body) {
        when(client.query(anyString(), any(Instant.class))).thenReturn(body);
        return service.query(payment);
    }

    // ------------------------------------------------------------------ bảng mã

    @Nested
    @DisplayName("Bảng mã TechSpec 2.1.0")
    class VerdictMapping {

        @Test
        @DisplayName("ResponseCode=91 ⇒ NOT_FOUND, được huỷ")
        void notFoundAllowsCancel() {
            VnpayQueryResult result = query(signedBody(response("91", "")));

            assertThat(result.verdict()).isEqualTo(VnpayQueryVerdict.NOT_FOUND);
            assertThat(result.verdict().allowsCancel()).isTrue();
        }

        @Test
        @DisplayName("00/00 ⇒ PAID, KHÔNG được huỷ")
        void paidDoesNotAllowCancel() {
            VnpayQueryResult result = query(signedBody(response("00", "00")));

            assertThat(result.verdict()).isEqualTo(VnpayQueryVerdict.PAID);
            assertThat(result.verdict().allowsCancel()).isFalse();
            assertThat(result.transactionNo()).isEqualTo("15718028");
            assertThat(result.bankCode()).isEqualTo("NCB");
            assertThat(result.payDate()).isEqualTo("20261028234453");
        }

        @Test
        @DisplayName("00/02 ⇒ FAILED, được huỷ")
        void failedAllowsCancel() {
            VnpayQueryResult result = query(signedBody(response("00", "02")));

            assertThat(result.verdict()).isEqualTo(VnpayQueryVerdict.FAILED);
            assertThat(result.verdict().allowsCancel()).isTrue();
        }

        @Test
        @DisplayName("00/01, 00/04, 00/05, 00/06, 00/07 và mã lạ ⇒ UNKNOWN, KHÔNG được huỷ")
        void ambiguousTransactionStatusesAreUnknown() {
            for (String status : new String[]{"01", "04", "05", "06", "07", "09", "", "99"}) {
                VnpayQueryResult result = query(signedBody(response("00", status)));

                assertThat(result.verdict())
                        .as("vnp_TransactionStatus=%s", status)
                        .isEqualTo(VnpayQueryVerdict.UNKNOWN);
                assertThat(result.verdict().allowsCancel())
                        .as("vnp_TransactionStatus=%s phải bị cấm huỷ", status)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("ResponseCode 02, 03, 94, 97, 99 ⇒ UNKNOWN, KHÔNG được huỷ")
        void otherResponseCodesAreUnknown() {
            for (String code : new String[]{"02", "03", "94", "97", "99"}) {
                VnpayQueryResult result = query(signedBody(response(code, "00")));

                assertThat(result.verdict())
                        .as("vnp_ResponseCode=%s", code)
                        .isEqualTo(VnpayQueryVerdict.UNKNOWN);
                assertThat(result.verdict().allowsCancel()).isFalse();
            }
        }
    }

    // ------------------------------------------------------------------ chống giả mạo & lệch

    @Nested
    @DisplayName("Chữ ký, mã giao dịch, số tiền")
    class Integrity {

        @Test
        @DisplayName("sai chữ ký ⇒ UNKNOWN dù ResponseCode=00 và TransactionStatus=00")
        void anInvalidSignatureOverridesASuccessfulCode() {
            Map<String, String> parameters = response("00", "00");

            VnpayQueryResult result = query(unsignedBody(parameters));

            assertThat(result.verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
            assertThat(result.verdict().allowsCancel()).isFalse();

            // Giữ lại mã thô để người vận hành thấy VNPay "nói" gì — nhưng không dùng nó để quyết.
            assertThat(result.responseCode()).isEqualTo("00");
            assertThat(result.message()).contains("Invalid signature");
        }

        @Test
        @DisplayName("nội dung bị sửa sau khi ký ⇒ UNKNOWN")
        void tamperedContentFailsVerification() {
            String body = signedBody(response("00", "02")); // FAILED nếu tin
            String tampered = body.replace("\"vnp_ResponseCode\":\"00\"",
                    "\"vnp_ResponseCode\":\"91\"");

            VnpayQueryResult result = query(tampered);

            assertThat(result.verdict())
                    .as("không được tin nội dung đã bị sửa, dù nó nói điều có lợi")
                    .isEqualTo(VnpayQueryVerdict.UNKNOWN);
        }

        @Test
        @DisplayName("vnp_TxnRef khác ⇒ UNKNOWN dù chữ ký hợp lệ")
        void aDifferentTxnRefIsRejected() {
            Map<String, String> parameters = response("00", "02");
            // Response của giao dịch KHÁC, được ký hợp lệ — nên chữ ký đúng mà vẫn phải từ chối.
            parameters.put("vnp_TxnRef", "someothertxnref");
            parameters.remove("vnp_SecureHash");

            VnpayQueryResult result = query(signedBody(parameters));

            assertThat(result.verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
            assertThat(result.message()).contains("vnp_TxnRef");
        }

        @Test
        @DisplayName("vnp_Amount khác ⇒ UNKNOWN dù chữ ký hợp lệ")
        void aDifferentAmountIsRejected() {
            Map<String, String> parameters = response("00", "02");
            parameters.put("vnp_Amount", "100");

            VnpayQueryResult result = query(signedBody(parameters));

            assertThat(result.verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
            assertThat(result.message()).contains("vnp_Amount");
        }
    }

    // ------------------------------------------------------------------ không hỏi được VNPay

    @Nested
    @DisplayName("Không hỏi được VNPay ⇒ UNKNOWN, không ném ngoại lệ")
    class TransportFailures {

        @Test
        @DisplayName("timeout / lỗi mạng ⇒ UNKNOWN")
        void aNetworkFailureIsUnknown() {
            when(client.query(anyString(), any(Instant.class)))
                    .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

            VnpayQueryResult result = service.query(payment);

            assertThat(result.verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
            assertThat(result.verdict().allowsCancel()).isFalse();
        }

        @Test
        @DisplayName("HTTP 500 ⇒ UNKNOWN")
        void anHttpErrorIsUnknown() {
            when(client.query(anyString(), any(Instant.class)))
                    .thenThrow(new org.springframework.web.client.HttpServerErrorException(
                            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

            assertThat(service.query(payment).verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
        }

        @Test
        @DisplayName("body không phải JSON, hoặc rỗng ⇒ UNKNOWN")
        void anUnparsableBodyIsUnknown() {
            assertThat(query("<html>502 Bad Gateway</html>").verdict())
                    .isEqualTo(VnpayQueryVerdict.UNKNOWN);
            assertThat(query("").verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
            assertThat(query(null).verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
            assertThat(query("[1,2,3]").verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
        }

        @Test
        @DisplayName("payment null, hoặc chưa có createdAt/txnRef ⇒ UNKNOWN, và KHÔNG gọi VNPay")
        void aPaymentThatCannotBeQueriedIsUnknown() {
            assertThat(service.query(null).verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);

            PaymentEntity notPersisted = PaymentEntity.builder()
                    .txnRef(TXN_REF)
                    .amount(AMOUNT)
                    .status(PaymentStatus.PENDING)
                    .build();

            assertThat(service.query(notPersisted).verdict()).isEqualTo(VnpayQueryVerdict.UNKNOWN);
            verify(client, org.mockito.Mockito.never()).query(anyString(), any(Instant.class));
        }
    }

    // ------------------------------------------------------------------ dữ liệu gửi đi

    @Nested
    @DisplayName("Tham số gửi VNPay")
    class RequestArguments {

        @Test
        @DisplayName("vnp_TransactionDate lấy từ payment.createdAt đọc trong DB")
        void theTransactionDateComesFromThePersistedCreatedAt() {
            when(client.query(anyString(), any(Instant.class))).thenReturn(signedBody(response("00", "00")));

            service.query(payment);

            verify(client).query(TXN_REF, Instant.parse("2026-10-28T16:44:53Z"));
        }
    }

    // ------------------------------------------------------------------ bất biến

    @Nested
    @DisplayName("Bất biến: chỉ FAILED và NOT_FOUND mới cho huỷ")
    class Invariant {

        @Test
        @DisplayName("PAID và UNKNOWN luôn allowsCancel() = false")
        void onlyTwoVerdictsAllowCancel() {
            assertThat(VnpayQueryVerdict.PAID.allowsCancel()).isFalse();
            assertThat(VnpayQueryVerdict.UNKNOWN.allowsCancel()).isFalse();
            assertThat(VnpayQueryVerdict.FAILED.allowsCancel()).isTrue();
            assertThat(VnpayQueryVerdict.NOT_FOUND.allowsCancel()).isTrue();

            assertThat(VnpayQueryVerdict.values())
                    .as("thêm verdict mới thì phải xem lại allowsCancel()")
                    .hasSize(4);
        }

        @Test
        @DisplayName("VnpayQueryResult.unknown() không thể tạo ra kết quả cho phép huỷ")
        void theUnknownFactoryCannotAllowCancel() {
            assertThat(VnpayQueryResult.unknown("bất kỳ").verdict().allowsCancel()).isFalse();
        }
    }

    // ------------------------------------------------------------------ bắt tham số

    @Nested
    @DisplayName("Đơn vị tiền")
    class AmountHandling {

        @Test
        @DisplayName("so khớp theo đơn vị VNPay (nhân 100), không theo đơn vị hiển thị")
        void amountsAreComparedInGatewayUnits() {
            Map<String, String> parameters = response("00", "00");
            parameters.put("vnp_Amount", "298800000"); // 2.988.000,00 × 100

            assertThat(query(signedBody(parameters)).verdict()).isEqualTo(VnpayQueryVerdict.PAID);

            // Nếu so nhầm theo đơn vị hiển thị (2988000) thì phải trượt.
            Map<String, String> wrongUnits = response("00", "00");
            wrongUnits.put("vnp_Amount", "2988000");

            assertThat(query(signedBody(wrongUnits)).verdict())
                    .isEqualTo(VnpayQueryVerdict.UNKNOWN);
        }
    }

    @Test
    @DisplayName("không gọi VNPay quá một lần cho một lần hỏi")
    void theGatewayIsCalledExactlyOnce() {
        when(client.query(anyString(), any(Instant.class)))
                .thenReturn(signedBody(response("00", "00")));

        service.query(payment);

        verify(client, org.mockito.Mockito.times(1)).query(anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("txnRef truyền cho client đúng bằng txnRef của payment")
    void theTxnRefIsTakenFromThePayment() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        when(client.query(captor.capture(), any(Instant.class)))
                .thenReturn(signedBody(response("00", "00")));

        service.query(payment);

        assertThat(captor.getValue()).isEqualTo(TXN_REF);
    }
}
