package com.structure.springboot.features.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body trả về cho VNPay sau khi xử lý IPN — <b>đúng hai khoá {@code RspCode} và {@code Message}</b>.
 *
 * <h2>Vì sao cần record này thay vì trả {@code Map<String, String>}</h2>
 *
 * <p>Trước đây controller tự dựng map và tự đặt tên khoá. Như vậy là để tầng HTTP quyết định
 * <b>giao thức của cổng thanh toán</b>, trong khi mã {@code 00/01/02/04/97/99} đã nằm ở tầng
 * nghiệp vụ ({@link VnpayIpnResponse}). Tên khoá và mã phản hồi là một hợp đồng với VNPay; chúng
 * phải nằm cùng một chỗ.</p>
 *
 * <h2>Vì sao phải có {@code @JsonProperty}</h2>
 *
 * <p>VNPay đọc đúng hai khoá viết hoa {@code RspCode} và {@code Message}. Jackson mặc định đổi
 * {@code rspCode} thành {@code "rspCode"} — sai chữ hoa, và VNPay coi như không có phản hồi rồi
 * gọi lại 10 lần. Không có cách nào để tên trường Java tự khớp, nên annotation là bắt buộc chứ
 * không phải trang trí.</p>
 *
 * <p>Đây là chỗ <b>dùng annotation lần đầu</b> trong các DTO của dự án (chỉ có
 * {@code @JsonInclude} ở {@code LoginResponse}). Ngoại lệ này có lý do cụ thể: tên khoá do bên thứ
 * ba quy định, không theo convention của mình.</p>
 *
 * @param rspCode mã VNPay quy định, xem các hằng {@code RSP_*} trong {@link VnpayIpnResponse}
 * @param message câu đi kèm, VNPay hiển thị trong portal của họ
 */
public record VnpayIpnAcknowledgement(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
) {

    /** Dựng body từ kết quả xử lý IPN. */
    public static VnpayIpnAcknowledgement from(VnpayIpnResponse response) {
        return new VnpayIpnAcknowledgement(response.rspCode(), response.message());
    }

    /**
     * Câu trả lời khi bản thân việc xử lý ném lỗi không lường trước.
     *
     * <p>{@code 99} = "lỗi không xác định, hãy gọi lại" — đúng ngữ nghĩa VNPay quy định cho mã
     * này. Đặt ở đây để tầng nào cũng dùng được mà không phải biết chuỗi {@code "99"}.</p>
     */
    public static VnpayIpnAcknowledgement unknownError() {
        return new VnpayIpnAcknowledgement(
                VnpayIpnResponse.RSP_UNKNOWN_ERROR, "Unknown error");
    }
}
