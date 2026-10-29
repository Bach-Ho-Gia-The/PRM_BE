package com.structure.springboot.infrastructure.external.vnpay;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Chữ ký cho lệnh {@code querydr} — hỏi VNPay kết quả một giao dịch.
 *
 * <h2>Vì sao phải là class riêng, không dùng lại {@link VnpaySignature}</h2>
 *
 * <p>{@code querydr} ký theo <b>một hệ hoàn toàn khác</b> với {@code pay} và IPN. Ba khác biệt, và
 * cả ba đều làm chữ ký sai nếu lẫn:</p>
 *
 * <table border="1">
 *   <caption>Hai hệ chữ ký</caption>
 *   <tr><th></th><th>{@code pay} / IPN / Return</th><th>{@code querydr}</th></tr>
 *   <tr>
 *     <td>Thứ tự</td>
 *     <td>Sắp xếp theo tên khoá ({@code TreeMap})</td>
 *     <td><b>Thứ tự cố định</b> theo tài liệu, không sắp xếp</td>
 *   </tr>
 *   <tr>
 *     <td>Dấu nối</td>
 *     <td>{@code &} giữa {@code khoá=giá trị}</td>
 *     <td>{@code |} giữa các <b>giá trị</b>, không có tên khoá</td>
 *   </tr>
 *   <tr>
 *     <td>URL-encode</td>
 *     <td><b>Có</b> (dấu cách thành {@code +}, {@code :} thành {@code %3A})</td>
 *     <td><b>Không</b> — ký trên chuỗi thô</td>
 *   </tr>
 * </table>
 *
 * <p><b>Trường rỗng vẫn phải giữ dấu {@code |}.</b> Đây là khác biệt thứ tư, và là khác biệt dễ
 * hỏng nhất: {@link VnpaySignature#canonicalQuery} <i>bỏ hẳn</i> tham số có giá trị rỗng, vì với
 * {@code pay} thì tham số rỗng không được gửi đi. Với {@code querydr}, chuỗi ký là một dãy vị trí
 * cố định — bỏ một vị trí là mọi vị trí sau dịch lên một, chữ ký lệch, VNPay từ chối. Vì vậy
 * {@code vnp_PromotionCode} rỗng vẫn chiếm đúng chỗ của nó với hai dấu {@code |} hai bên.</p>
 *
 * <p><b>Phần dùng chung duy nhất là hàm băm.</b> HMAC-SHA512 hex chữ thường thì hai hệ giống hệt
 * nhau, nên lớp này gọi lại {@link VnpaySignature#sign(String, String)} thay vì chép lại mã. Chỉ
 * phần <i>dựng chuỗi</i> là khác, và đó chính là phần nằm ở đây.</p>
 */
public final class VnpayQuerySignature {

    /**
     * Thứ tự 9 trường của chuỗi ký <b>request</b>, theo TechSpec VNPay 2.1.0 §2.5.
     *
     * <p>Khai báo thành danh sách tường minh chứ không dựa vào thứ tự {@code Map}: thứ tự này là
     * một phần của giao thức, không phải chi tiết cài đặt. Nhìn danh sách là thấy ngay chuỗi ký
     * gồm những gì, đúng thứ tự nào.</p>
     */
    private static final List<String> REQUEST_FIELDS = List.of(
            "vnp_RequestId",
            "vnp_Version",
            "vnp_Command",
            "vnp_TmnCode",
            "vnp_TxnRef",
            "vnp_TransactionDate",
            "vnp_CreateDate",
            "vnp_IpAddr",
            "vnp_OrderInfo"
    );

    /**
     * Thứ tự 15 trường của chuỗi ký <b>response</b>.
     *
     * <p>Nhiều hơn request 6 trường và <b>không</b> phải phần mở rộng của request — khác cả thứ tự
     * lẫn thành phần ({@code vnp_TransactionType} chỉ có ở response, {@code vnp_Version} chỉ có ở
     * request). Dùng chung một hàm cho hai chiều là bảo đảm sai.</p>
     */
    private static final List<String> RESPONSE_FIELDS = List.of(
            "vnp_ResponseId",
            "vnp_Command",
            "vnp_ResponseCode",
            "vnp_Message",
            "vnp_TmnCode",
            "vnp_TxnRef",
            "vnp_Amount",
            "vnp_BankCode",
            "vnp_PayDate",
            "vnp_TransactionNo",
            "vnp_TransactionType",
            "vnp_TransactionStatus",
            "vnp_OrderInfo",
            "vnp_PromotionCode",
            "vnp_PromotionAmount"
    );

    private VnpayQuerySignature() {
    }

    /** Chuỗi ký của request gửi VNPay: 9 giá trị, nối bằng {@code |}, theo đúng thứ tự tài liệu. */
    public static String requestCanonical(Map<String, String> parameters) {
        return canonical(parameters, REQUEST_FIELDS);
    }

    /** Chuỗi ký của response VNPay trả về: 15 giá trị, nối bằng {@code |}. */
    public static String responseCanonical(Map<String, String> parameters) {
        return canonical(parameters, RESPONSE_FIELDS);
    }

    /**
     * Dựng chuỗi ký từ danh sách trường cố định.
     *
     * <p>Trường vắng mặt hoặc {@code null} được coi là <b>chuỗi rỗng</b> và vẫn giữ vị trí của nó.
     * Không bỏ qua, không ném lỗi: thiếu một trường thì việc chữ ký sai sẽ do VNPay báo, còn ném
     * lỗi ở đây thì biến một response lạ thành sự cố của chính mình.</p>
     */
    private static String canonical(Map<String, String> parameters, List<String> fields) {
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                builder.append('|');
            }

            String value = parameters == null ? null : parameters.get(fields.get(index));
            builder.append(value == null ? "" : value);
        }

        return builder.toString();
    }

    /**
     * Xác minh chữ ký của response.
     *
     * <p>So sánh bằng {@link MessageDigest#isEqual} chứ không bằng {@link String#equals}: đây là
     * chữ ký nhận từ Internet, so sánh thoát sớm sẽ để lộ thông tin qua thời gian phản hồi.</p>
     *
     * <p><b>Sai chữ ký thì không được tin bất cứ thứ gì khác trong response</b>, kể cả
     * {@code vnp_ResponseCode = "00"}. Một response giả mạo nói "đã thanh toán" còn nguy hiểm hơn
     * một response lỗi.</p>
     *
     * @param parameters toàn bộ response, đã tách thành chuỗi
     * @param hashSecret khoá bí mật của merchant
     * @return {@code true} nếu chữ ký khớp
     */
    public static boolean verifyResponse(
            Map<String, String> parameters,
            String hashSecret
    ) {
        String receivedHash = parameters == null ? null : parameters.get("vnp_SecureHash");

        // Độ dài cố định 128 ký tự của SHA-512 hex. Chặn sớm để không phải parse chuỗi rác.
        if (receivedHash == null || receivedHash.length() != 128) {
            return false;
        }

        byte[] receivedBytes;
        try {
            receivedBytes = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException exception) {
            // Có ký tự không phải hex ⇒ chữ ký không hợp lệ, không phải lỗi hệ thống.
            return false;
        }

        byte[] expectedBytes;
        try {
            expectedBytes = HexFormat.of().parseHex(
                    VnpaySignature.sign(responseCanonical(parameters), hashSecret));
        } catch (IllegalArgumentException exception) {
            // Không xảy ra với đầu ra của HMAC (luôn là hex hợp lệ) — nhưng vẫn trả false thay vì
            // để một IllegalArgumentException lọt lên tầng gọi.
            return false;
        }

        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    /**
     * Ký chuỗi canonical của request.
     *
     * <p>Tách khỏi phần dựng chuỗi để test gọi được từng phần: sai ở khâu dựng chuỗi và sai ở khâu
     * băm là hai loại lỗi khác nhau, gộp lại thì không biết cái nào hỏng.</p>
     */
    public static String signRequest(Map<String, String> parameters, String hashSecret) {
        return VnpaySignature.sign(requestCanonical(parameters), hashSecret);
    }

}
