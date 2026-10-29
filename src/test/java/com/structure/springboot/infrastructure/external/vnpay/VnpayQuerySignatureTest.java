package com.structure.springboot.infrastructure.external.vnpay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chứng minh chữ ký {@code querydr} là <b>một hệ khác</b> với {@code pay}.
 *
 * <p>Năm khác biệt được kiểm riêng từng cái, vì mỗi cái đều là một cách làm chữ ký sai mà test của
 * {@code pay} không bắt được: thứ tự, dấu nối, không URL-encode, trường rỗng vẫn giữ vị trí, và số
 * trường của request khác response.</p>
 */
class VnpayQuerySignatureTest {

    private static final String HASH_SECRET = "TEST_HASH_SECRET_4F9A2C";

    private static Map<String, String> requestParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("vnp_RequestId", "a1b2c3d4e5f6");
        parameters.put("vnp_Version", "2.1.0");
        parameters.put("vnp_Command", "querydr");
        parameters.put("vnp_TmnCode", "6BSNA5CW");
        parameters.put("vnp_TxnRef", "105df48f66fc4918a91ddd2f5309cf22");
        parameters.put("vnp_TransactionDate", "20261028234453");
        parameters.put("vnp_CreateDate", "20261029101010");
        parameters.put("vnp_IpAddr", "127.0.0.1");
        parameters.put("vnp_OrderInfo", "Truy van 105df48f66fc4918a91ddd2f5309cf22");
        return parameters;
    }

    private static Map<String, String> responseParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("vnp_ResponseId", "r-001");
        parameters.put("vnp_Command", "querydr");
        parameters.put("vnp_ResponseCode", "00");
        parameters.put("vnp_Message", "QueryDR Success");
        parameters.put("vnp_TmnCode", "6BSNA5CW");
        parameters.put("vnp_TxnRef", "105df48f66fc4918a91ddd2f5309cf22");
        parameters.put("vnp_Amount", "298800000");
        parameters.put("vnp_BankCode", "NCB");
        parameters.put("vnp_PayDate", "20261028234453");
        parameters.put("vnp_TransactionNo", "15718028");
        parameters.put("vnp_TransactionType", "01");
        parameters.put("vnp_TransactionStatus", "00");
        parameters.put("vnp_OrderInfo", "Thanh toan 105df48f66fc4918a91ddd2f5309cf22");
        parameters.put("vnp_PromotionCode", "");
        parameters.put("vnp_PromotionAmount", "");
        return parameters;
    }

    @Nested
    @DisplayName("Chuỗi ký request: 9 trường, nối |, đúng thứ tự tài liệu")
    class RequestCanonical {

        @Test
        @DisplayName("đúng nguyên văn thứ tự TechSpec 2.1.0 §2.5")
        void theFieldOrderFollowsTheSpecification() {
            assertThat(VnpayQuerySignature.requestCanonical(requestParameters()))
                    .isEqualTo("a1b2c3d4e5f6"
                            + "|2.1.0"
                            + "|querydr"
                            + "|6BSNA5CW"
                            + "|105df48f66fc4918a91ddd2f5309cf22"
                            + "|20261028234453"
                            + "|20261029101010"
                            + "|127.0.0.1"
                            + "|Truy van 105df48f66fc4918a91ddd2f5309cf22");
        }

        @Test
        @DisplayName("KHÔNG sắp xếp theo tên khoá như VnpaySignature")
        void theFieldsAreNotSorted() {
            String canonical = VnpayQuerySignature.requestCanonical(requestParameters());

            // Nếu sắp xếp theo tên khoá, vnp_Command đứng đầu (vnp_Command < vnp_CreateDate < ...).
            // Chuỗi đúng phải bắt đầu bằng vnp_RequestId — khoá đứng CUỐI nếu sắp xếp.
            assertThat(canonical).startsWith("a1b2c3d4e5f6|2.1.0");
            assertThat(canonical).doesNotStartWith("querydr");

            // Đối chứng: kiểu sắp xếp cho ra thứ tự khác hẳn.
            String sorted = new TreeMap<>(requestParameters()).values().stream()
                    .reduce((left, right) -> left + "|" + right)
                    .orElseThrow();
            assertThat(canonical).isNotEqualTo(sorted);
        }

        @Test
        @DisplayName("KHÔNG URL-encode — dấu cách và dấu hai chấm giữ nguyên")
        void theValuesAreNotUrlEncoded() {
            Map<String, String> parameters = requestParameters();
            parameters.put("vnp_OrderInfo", "Truy van: 50% da tra");

            String canonical = VnpayQuerySignature.requestCanonical(parameters);

            assertThat(canonical).contains("Truy van: 50% da tra");
            assertThat(canonical).doesNotContain("+");
            assertThat(canonical).doesNotContain("%3A");
        }

        @Test
        @DisplayName("trường rỗng VẪN giữ dấu | — khác hẳn VnpaySignature vốn bỏ trường rỗng")
        void emptyValuesKeepTheirSeparator() {
            Map<String, String> parameters = requestParameters();
            parameters.put("vnp_OrderInfo", "");

            String canonical = VnpayQuerySignature.requestCanonical(parameters);

            // 8 dấu | cho 9 trường, kể cả khi trường cuối rỗng.
            assertThat(canonical.chars().filter(character -> character == '|').count())
                    .isEqualTo(8);
            assertThat(canonical).endsWith("|127.0.0.1|");

            // Đối chứng với kiểu `pay`: trường rỗng bị bỏ hẳn nên chuỗi ngắn hơn.
            assertThat(canonical.chars().filter(character -> character == '|').count())
                    .isNotEqualTo(7);
        }

        @Test
        @DisplayName("trường vắng mặt coi như rỗng, vẫn giữ vị trí")
        void missingFieldsBehaveLikeEmptyOnes() {
            Map<String, String> parameters = requestParameters();
            parameters.remove("vnp_IpAddr");

            assertThat(VnpayQuerySignature.requestCanonical(parameters))
                    .isEqualTo(VnpayQuerySignature.requestCanonical(withEmptyIp()));
        }

        private Map<String, String> withEmptyIp() {
            Map<String, String> parameters = requestParameters();
            parameters.put("vnp_IpAddr", "");
            return parameters;
        }
    }

    @Nested
    @DisplayName("Chuỗi ký response: 15 trường, thứ tự riêng")
    class ResponseCanonical {

        @Test
        @DisplayName("đúng 15 giá trị nối bằng 14 dấu |")
        void theResponseHasFifteenFields() {
            String canonical = VnpayQuerySignature.responseCanonical(responseParameters());

            assertThat(canonical.chars().filter(character -> character == '|').count())
                    .isEqualTo(14);
        }

        @Test
        @DisplayName("vnp_TransactionStatus đứng TRƯỚC vnp_OrderInfo — đảo hai vị trí là chữ ký sai")
        void theTransactionStatusComesBeforeTheOrderInfo() {
            String canonical = VnpayQuerySignature.responseCanonical(responseParameters());

            assertThat(canonical.indexOf("|00|Thanh toan"))
                    .as("01 (TransactionType) rồi 00 (TransactionStatus) rồi OrderInfo")
                    .isPositive();

            // Đảo hai giá trị trong map không đổi thứ tự chuỗi ký — thứ tự do code quyết, không do
            // map quyết. Đây chính là điểm phải khoá: nếu ai đó sắp xếp lại danh sách trường, test
            // này đỏ.
            Map<String, String> swapped = new LinkedHashMap<>();
            responseParameters().forEach((key, value) -> {
                if ("vnp_OrderInfo".equals(key)) {
                    swapped.put("vnp_TransactionStatus", value);
                } else if ("vnp_TransactionStatus".equals(key)) {
                    swapped.put("vnp_OrderInfo", value);
                } else {
                    swapped.put(key, value);
                }
            });

            assertThat(VnpayQuerySignature.responseCanonical(swapped))
                    .as("thứ tự do danh sách trường quyết, không do thứ tự map")
                    .isNotEqualTo(canonical);
        }

        @Test
        @DisplayName("request và response KHÔNG dùng chung chuỗi ký")
        void requestAndResponseUseDifferentCanonicalStrings() {
            Map<String, String> shared = new LinkedHashMap<>();
            shared.put("vnp_TxnRef", "abc");
            shared.put("vnp_Command", "querydr");

            assertThat(VnpayQuerySignature.requestCanonical(shared))
                    .isNotEqualTo(VnpayQuerySignature.responseCanonical(shared));
        }
    }

    @Nested
    @DisplayName("Chữ ký khác hệ với pay")
    class DifferentFromPaySignature {

        @Test
        @DisplayName("cùng bộ tham số, chữ ký querydr KHÁC chữ ký pay")
        void theQuerySignatureDiffersFromThePaymentSignature() {
            Map<String, String> parameters = requestParameters();

            String queryHash = VnpayQuerySignature.signRequest(parameters, HASH_SECRET);
            String payHash = VnpaySignature.sign(
                    VnpaySignature.canonicalQuery(parameters), HASH_SECRET);

            assertThat(queryHash)
                    .as("nếu hai hệ cho cùng kết quả thì đang dùng lẫn công thức")
                    .isNotEqualTo(payHash);

            // Cùng độ dài 128 hex — khác nội dung, không khác định dạng.
            assertThat(queryHash).hasSize(128);
            assertThat(queryHash).isEqualTo(queryHash.toLowerCase());
        }
    }

    @Nested
    @DisplayName("Xác minh chữ ký response")
    class VerifyResponse {

        private Map<String, String> signedResponse() {
            Map<String, String> parameters = responseParameters();
            // Ký bằng API công khai có sẵn — không thêm hàm nào vào src/main chỉ để test dùng.
            parameters.put("vnp_SecureHash", VnpaySignature.sign(
                    VnpayQuerySignature.responseCanonical(parameters), HASH_SECRET));
            return parameters;
        }

        @Test
        @DisplayName("chữ ký đúng ⇒ true")
        void aCorrectSignatureVerifies() {
            assertThat(VnpayQuerySignature.verifyResponse(signedResponse(), HASH_SECRET)).isTrue();
        }

        @Test
        @DisplayName("sai chữ ký ⇒ false, kể cả khi ResponseCode = 00")
        void aWrongSignatureFailsEvenWhenTheResponseCodeIsZero() {
            Map<String, String> parameters = signedResponse();
            parameters.put("vnp_ResponseCode", "00");
            parameters.put("vnp_TransactionStatus", "00");
            // Đổi mã SAU khi đã ký — đúng kiểu tấn công sửa nội dung.
            parameters.put("vnp_Amount", "999999999");

            assertThat(VnpayQuerySignature.verifyResponse(parameters, HASH_SECRET)).isFalse();
        }

        @Test
        @DisplayName("đảo thứ tự hai giá trị trong map ⇒ chữ ký vẫn đúng, vì thứ tự do code quyết")
        void mapIterationOrderDoesNotAffectVerification() {
            Map<String, String> parameters = signedResponse();

            assertThat(VnpayQuerySignature.verifyResponse(parameters, HASH_SECRET)).isTrue();
        }

        @Test
        @DisplayName("thiếu vnp_SecureHash, sai độ dài, hoặc có ký tự không phải hex ⇒ false")
        void malformedHashesAreRejected() {
            Map<String, String> missing = signedResponse();
            missing.remove("vnp_SecureHash");
            assertThat(VnpayQuerySignature.verifyResponse(missing, HASH_SECRET)).isFalse();

            Map<String, String> tooShort = signedResponse();
            tooShort.put("vnp_SecureHash", "abc123");
            assertThat(VnpayQuerySignature.verifyResponse(tooShort, HASH_SECRET)).isFalse();

            Map<String, String> notHex = signedResponse();
            notHex.put("vnp_SecureHash", "z".repeat(128));
            assertThat(VnpayQuerySignature.verifyResponse(notHex, HASH_SECRET)).isFalse();

            assertThat(VnpayQuerySignature.verifyResponse(null, HASH_SECRET)).isFalse();
        }

        @Test
        @DisplayName("sai hash secret ⇒ false")
        void aDifferentSecretFails() {
            assertThat(VnpayQuerySignature.verifyResponse(signedResponse(), "OTHER_SECRET"))
                    .isFalse();
        }
    }
}
