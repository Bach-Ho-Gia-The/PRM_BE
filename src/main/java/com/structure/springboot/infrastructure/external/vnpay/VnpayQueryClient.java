package com.structure.springboot.infrastructure.external.vnpay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gọi lệnh {@code querydr} sang VNPay: hỏi kết quả một giao dịch đã tạo.
 *
 * <h2>Lớp này chỉ nói chuyện HTTP, không quyết định gì</h2>
 *
 * <p>Nó <b>không</b> xác minh chữ ký, <b>không</b> đối chiếu số tiền, <b>không</b> kết luận giao dịch
 * thành công hay thất bại. Chỉ dựng request, ký, gửi, trả về nguyên văn những gì VNPay nói. Toàn bộ
 * việc đánh giá nằm ở {@code VnpayQueryServiceImpl} — nơi test được bằng dữ liệu dựng sẵn mà không
 * cần mạng. Một lớp vừa gọi mạng vừa ra quyết định thì chỉ test được một trong hai.</p>
 *
 * <h2>Mọi thứ liên quan tới định dạng dây (wire) đều nằm trong lớp này</h2>
 *
 * <p>Người gọi truyền vào {@link Instant} thô, lớp này tự đổi ra chuỗi. Nhờ vậy chỉ có <b>một</b> chỗ
 * trong toàn hệ format {@code yyyyMMddHHmmss} cho lệnh tra cứu, và nó nằm cạnh chỗ dùng. Nếu để
 * tầng service format rồi truyền chuỗi xuống, sớm muộn sẽ có hai chỗ format lệch nhau một chữ số.</p>
 *
 * <h2>Timeout là bắt buộc, không phải tuỳ chọn</h2>
 *
 * <p>Lệnh này về sau được gọi từ tác vụ quét đơn hết hạn, chạy <b>mỗi 1 phút</b>. Không đặt timeout
 * thì {@code RestClient} dùng mặc định của thư viện nền — có thể chờ rất lâu — và một cú gọi treo sẽ
 * giữ chỗ cả lượt quét, lượt sau lại chồng lên lượt trước. Ba giây mở kết nối và mười giây đọc là mức
 * đủ rộng cho một API tra cứu, đủ hẹp để lượt quét không bị kẹt.</p>
 *
 * <p><b>Vì sao hằng số chứ không thành khoá cấu hình:</b> đây là con số kỹ thuật bảo vệ vòng quét,
 * không phải chính sách kinh doanh mà vận hành cần chỉnh. Mọi khoá cấu hình đều có thể bị đặt sai
 * trong file {@code .env} của một máy nào đó, và một timeout đặt thành mười phút sẽ làm hỏng đúng cơ
 * chế mà nó sinh ra để bảo vệ.</p>
 *
 * <h2>Lỗi mạng được ném ra ngoài, không bị nuốt</h2>
 *
 * <p>{@code RestClient} ném {@link org.springframework.web.client.RestClientException} khi không kết
 * nối được, khi timeout, và cả khi server trả mã lỗi HTTP. Lớp này để nguyên: người gọi phải phân
 * biệt được "VNPay nói giao dịch thất bại" (một câu trả lời bình thường) với "không hỏi được VNPay"
 * (không có câu trả lời nào). Nuốt lỗi ở đây sẽ biến ca thứ hai thành ca thứ nhất, và đó chính là
 * cách huỷ đơn của một người đã trả tiền.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayQueryClient {

    /**
     * Lệnh tra cứu. <b>Hằng số riêng, cố ý không dùng {@code properties.command()}.</b>
     *
     * <p>{@code app.payment.vnpay.command} trong yaml đang là {@code pay} — đúng cho việc dựng link
     * thanh toán. Dùng lại biến đó ở đây thì hoặc {@code querydr} gửi nhầm lệnh {@code pay}, hoặc
     * người sửa yaml để phục vụ tra cứu sẽ làm hỏng luôn link thanh toán.</p>
     */
    private static final String QUERY_COMMAND = "querydr";

    /**
     * IP gửi kèm request. Đây là gọi <b>server-to-server</b> nên không có IP khách để dùng.
     *
     * <p>{@code 127.0.0.1} là địa chỉ loopback — đúng nghĩa "chính máy này đang gọi", không mạo nhận
     * IP của ai. Spec bắt buộc trường này phải có, không được để trống.</p>
     */
    private static final String SERVER_IP = "127.0.0.1";

    private static final String ORDER_INFO_PREFIX = "Truy van ";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Cùng pattern với {@link VnpayPaymentUrlBuilder}. Hai lớp phải dùng chung một định dạng, vì
     * {@code vnp_TransactionDate} chính là {@code vnp_CreateDate} của lần tạo link — nếu hai bên
     * format khác nhau thì VNPay coi là hai giao dịch khác nhau.
     */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayProperties properties;

    // Khởi tạo tại chỗ, cùng khuôn với BrevoMailClient: RestClient không có trạng thái nên dùng
    // lại được cho mọi request, và Lombok bỏ qua field final đã gán sẵn.
    private final RestClient restClient = buildRestClient();

    /**
     * Hỏi VNPay về một giao dịch.
     *
     * @param txnRef          mã giao dịch đã gửi lúc tạo link thanh toán
     * @param transactionDate thời điểm tạo payment — <b>phải là giá trị đọc từ DB</b>, vì đó cũng là
     *                        nguồn đã dùng để dựng {@code vnp_CreateDate} của link thanh toán
     * @return nguyên văn body VNPay trả về, <b>chưa</b> được kiểm tra gì
     * @throws org.springframework.web.client.RestClientException khi không gọi được, timeout, hoặc
     *                                                           VNPay trả mã lỗi HTTP
     */
    public String query(String txnRef, Instant transactionDate) {
        String requestId = newRequestId();

        Map<String, String> parameters = Map.of(
                "vnp_RequestId", requestId,
                "vnp_Version", properties.version(),
                "vnp_Command", QUERY_COMMAND,
                "vnp_TmnCode", properties.tmnCode(),
                "vnp_TxnRef", txnRef,
                "vnp_TransactionDate", format(transactionDate),
                "vnp_CreateDate", format(Instant.now()),
                "vnp_IpAddr", SERVER_IP,
                "vnp_OrderInfo", ORDER_INFO_PREFIX + txnRef
        );

        // Ký trên ĐÚNG bộ tham số sắp gửi: lệch một ký tự giữa thứ đã ký và thứ đã gửi là lỗi khó
        // thấy nhất trong loại tích hợp này, nên không tính lại từ nguồn khác.
        Map<String, String> payload = new HashMap<>(parameters);
        payload.put("vnp_SecureHash",
                VnpayQuerySignature.signRequest(parameters, properties.hashSecret()));

        log.debug("querydr txnRef={} requestId={}", txnRef, requestId);

        return restClient.post()
                .uri(properties.apiUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    private String format(Instant value) {
        return DATE_FORMAT.withZone(properties.zoneId()).format(value);
    }

    private static RestClient buildRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Mã định danh cho mỗi lần hỏi. Spec yêu cầu không trùng giữa các request.
     *
     * <p>Sinh cùng kiểu với {@code txnRef} của payment (UUID bỏ dấu gạch) để cả hệ chỉ có một quy
     * ước về mã định danh.</p>
     */
    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
