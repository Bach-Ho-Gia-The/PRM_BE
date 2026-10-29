package com.structure.springboot.features.payment.service.impl;

import com.structure.springboot.features.payment.dto.response.VnpayQueryResult;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.VnpayQueryVerdict;
import com.structure.springboot.features.payment.service.VnpayQueryService;
import com.structure.springboot.infrastructure.external.vnpay.VnpayAmounts;
import com.structure.springboot.infrastructure.external.vnpay.VnpayProperties;
import com.structure.springboot.infrastructure.external.vnpay.VnpayQueryClient;
import com.structure.springboot.infrastructure.external.vnpay.VnpayQuerySignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Đánh giá câu trả lời của VNPay cho lệnh {@code querydr}.
 *
 * <h2>Thứ tự kiểm tra, và vì sao thứ tự này quan trọng</h2>
 *
 * <ol>
 *   <li><b>Gọi được không</b> — lỗi mạng, timeout, HTTP lỗi ⇒ {@code UNKNOWN}. Chưa có response thì
 *       chưa có gì để đọc.</li>
 *   <li><b>Có phải JSON không</b> — VNPay có thể trả trang HTML lỗi kèm HTTP 200. Parse hỏng ⇒
 *       {@code UNKNOWN}.</li>
 *   <li><b>Chữ ký có đúng không</b> — <b>trước</b> khi đọc bất cứ trường nào khác, kể cả
 *       {@code vnp_ResponseCode}. Một response giả mạo nói "đã thanh toán" còn nguy hiểm hơn một
 *       response lỗi. Đọc mã trước rồi mới verify là đã tin dữ liệu chưa xác thực.</li>
 *   <li><b>Mã giao dịch có khớp không</b> — response của giao dịch khác thì không nói gì về giao dịch
 *       này.</li>
 *   <li><b>Số tiền có khớp không</b> — lệch tiền nghĩa là lệch giao dịch.</li>
 *   <li><b>Map mã sang verdict</b> — chỉ tới đây mới được kết luận.</li>
 * </ol>
 *
 * <p>Mọi bước 1–5 hỏng đều dẫn về {@link VnpayQueryResult#unknown(String)} — hàm dựng sẵn
 * <b>không thể</b> tạo ra kết quả cho phép huỷ đơn.</p>
 *
 * <h2>Bảng mã đã chốt</h2>
 *
 * <p>Theo TechSpec VNPay 2.1.0, chỉ hai tổ hợp cho phép huỷ đơn:</p>
 *
 * <ul>
 *   <li>{@code vnp_ResponseCode = "91"} ⇒ {@code NOT_FOUND}</li>
 *   <li>{@code vnp_ResponseCode = "00"} và {@code vnp_TransactionStatus = "02"} ⇒ {@code FAILED}</li>
 * </ul>
 *
 * <p>Các mã {@code 02}, {@code 03}, {@code 94}, {@code 97}, {@code 99} ở {@code vnp_ResponseCode} — và
 * mọi giá trị không có trong danh sách trên — đều là {@code UNKNOWN}. Đặc biệt {@code 04} (đảo giao
 * dịch) và {@code 05}/{@code 06} (hoàn tiền) <b>không</b> phải {@code FAILED}: tiền đã chạy.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VnpayQueryServiceImpl implements VnpayQueryService {

    private static final String RESPONSE_CODE_SUCCESS = "00";
    private static final String RESPONSE_CODE_NOT_FOUND = "91";

    private static final String TRANSACTION_STATUS_SUCCESS = "00";
    private static final String TRANSACTION_STATUS_FAILED = "02";

    private final VnpayQueryClient vnpayQueryClient;
    private final VnpayProperties vnpayProperties;
    private final ObjectMapper objectMapper;

    @Override
    public VnpayQueryResult query(PaymentEntity payment) {
        if (payment == null) {
            return VnpayQueryResult.unknown("No payment to query");
        }

        if (payment.getTxnRef() == null || payment.getCreatedAt() == null) {
            // Chưa lưu thì không có mốc thời gian để dựng vnp_TransactionDate — bịa ra một mốc thì
            // VNPay sẽ trả lời về một giao dịch khác.
            return VnpayQueryResult.unknown("Payment has no txnRef or createdAt");
        }

        String body;
        try {
            body = vnpayQueryClient.query(payment.getTxnRef(), payment.getCreatedAt());
        } catch (RestClientException exception) {
            // Gồm cả timeout, không kết nối được, và HTTP 4xx/5xx.
            log.warn("querydr txnRef={} failed: {}", payment.getTxnRef(), exception.getMessage());
            return VnpayQueryResult.unknown("VNPay call failed: " + exception.getMessage());
        }

        Map<String, String> parameters = parse(body, payment.getTxnRef());
        if (parameters == null) {
            return VnpayQueryResult.unknown("VNPay response is not a JSON object");
        }

        VnpayQueryResult parsed = toResult(parameters);

        // Chữ ký TRƯỚC khi đọc bất cứ trường nào khác.
        if (!VnpayQuerySignature.verifyResponse(parameters, vnpayProperties.hashSecret())) {
            log.warn("querydr txnRef={} has an invalid signature", payment.getTxnRef());
            return VnpayQueryResult.unknownOf(parsed, "Invalid signature in VNPay response");
        }

        String responseTxnRef = parameters.get("vnp_TxnRef");
        if (responseTxnRef == null || !responseTxnRef.equals(payment.getTxnRef())) {
            return VnpayQueryResult.unknownOf(parsed, "vnp_TxnRef does not match the queried payment");
        }

        if (!amountMatches(parsed, payment)) {
            return VnpayQueryResult.unknownOf(parsed, "vnp_Amount does not match the payment amount");
        }

        return parsed;
    }

    /**
     * Đọc body thành map trường VNPay.
     *
     * <p>Chỉ nhận trường dạng chuỗi hoặc số — chuyển số về chuỗi để chữ ký tính trên đúng giá trị đã
     * nhận. {@code vnp_Amount} VNPay trả dạng số, nhưng nếu khai báo JSON là chuỗi thì cũng đọc được.</p>
     *
     * @return {@code null} khi body không phải JSON object
     */
    private Map<String, String> parse(String body, String txnRef) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(body);

            if (root == null || !root.isObject()) {
                return null;
            }

            Map<String, String> parameters = new LinkedHashMap<>();
            root.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull()) {
                    // asString() chuyển cả số, boolean và chuỗi về đúng biểu diễn của nó.
                    parameters.put(entry.getKey(), value.asString());
                }
            });
            return parameters;

        } catch (RuntimeException exception) {
            // readTree ném JacksonException (RuntimeException ở Jackson 3) với body không phải JSON.
            log.warn("querydr txnRef={} returned an unparsable body", txnRef);
            return null;
        }
    }

    /** Đọc các trường cần ghi log + map mã. <b>Chưa</b> kiểm chữ ký, chưa kết luận. */
    private VnpayQueryResult toResult(Map<String, String> parameters) {
        String responseCode = parameters.get("vnp_ResponseCode");
        String transactionStatus = parameters.get("vnp_TransactionStatus");

        return new VnpayQueryResult(
                toVerdict(responseCode, transactionStatus, parameters),
                responseCode,
                transactionStatus,
                parameters.get("vnp_PayDate"),
                parameters.get("vnp_TransactionNo"),
                parameters.get("vnp_BankCode"),
                parameters.get("vnp_Amount"),
                parameters.get("vnp_Message")
        );
    }

    /**
     * Bảng mã → verdict. Đây là <b>chỗ duy nhất</b> trong hệ biến câu trả lời của VNPay thành quyền
     * được huỷ đơn hay không.
     */
    private VnpayQueryVerdict toVerdict(
            String responseCode,
            String transactionStatus,
            Map<String, String> parameters
    ) {
        if (RESPONSE_CODE_NOT_FOUND.equals(responseCode)) {
            return VnpayQueryVerdict.NOT_FOUND;
        }

        if (RESPONSE_CODE_SUCCESS.equals(responseCode)) {
            if (TRANSACTION_STATUS_SUCCESS.equals(transactionStatus)) {
                return VnpayQueryVerdict.PAID;
            }

            if (TRANSACTION_STATUS_FAILED.equals(transactionStatus)) {
                return VnpayQueryVerdict.FAILED;
            }

            // 01 chưa hoàn tất, 04 giao dịch đảo, 05/06 hoàn tiền, 07 nghi ngờ, và mọi mã khác.
            // Không mã nào trong số này nói "khách chưa từng trả tiền".
            log.info("querydr responseCode=00 with transactionStatus={} is not conclusive",
                    transactionStatus);
            return VnpayQueryVerdict.UNKNOWN;
        }

        // 02, 03, 94, 97, 99 và mọi mã lạ.
        return VnpayQueryVerdict.UNKNOWN;
    }


    /**
     * Số tiền trong response có khớp số tiền của payment không.
     *
     * <p>Đổi đơn vị qua {@link VnpayAmounts} — cùng hàm mà luồng dựng link dùng để gửi đi. Không đổi
     * được (số tiền lưu hỏng) thì coi là <b>không khớp</b>: đây là luồng đối soát, và "không khớp"
     * dẫn tới {@code UNKNOWN}, tức là không huỷ đơn. Ném ngoại lệ ở đây sẽ biến một bản ghi hỏng
     * thành sự cố của cả lượt quét.</p>
     */
    private boolean amountMatches(VnpayQueryResult result, PaymentEntity payment) {
        String raw = result.amount();
        if (raw == null || raw.isBlank()) {
            return false;
        }

        BigInteger gatewayAmount;
        try {
            gatewayAmount = new BigInteger(raw.trim());
        } catch (NumberFormatException exception) {
            return false;
        }

        return VnpayAmounts.findGatewayAmount(payment.getAmount())
                .filter(expected -> expected.signum() > 0)
                .map(expected -> expected.compareTo(gatewayAmount) == 0)
                .orElse(false);
    }
}
