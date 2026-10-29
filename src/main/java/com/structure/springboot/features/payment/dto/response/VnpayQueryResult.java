package com.structure.springboot.features.payment.dto.response;

import com.structure.springboot.features.payment.enums.VnpayQueryVerdict;

/**
 * Kết quả một lần hỏi VNPay bằng {@code querydr}, đã được đánh giá.
 *
 * <p>Khác với {@code VnpayQueryClient} (trả nguyên văn body), record này là <b>kết luận</b>: đã xác
 * minh chữ ký, đã đối chiếu mã giao dịch và số tiền, đã map sang {@link VnpayQueryVerdict}.</p>
 *
 * <h2>Vì sao vẫn giữ các trường thô bên cạnh verdict</h2>
 *
 * <p>Để ghi log và để đối soát về sau. Khi một đơn rơi vào {@code UNKNOWN}, người vận hành cần biết
 * <b>vì sao</b>: VNPay nói mã gì, hay không nói gì cả. Chỉ có verdict thì không trả lời được câu đó —
 * và đây là loại sự cố mà "không có log" nghĩa là phải ngồi đoán.</p>
 *
 * <p>Các trường này <b>không</b> phải nguồn dữ liệu cho nghiệp vụ: không ai được ghi
 * {@code VnpayQueryResult} vào database. Trạng thái thanh toán thật vẫn chỉ do IPN ghi.</p>
 *
 * @param verdict           kết luận — xem {@link VnpayQueryVerdict#allowsCancel()}
 * @param responseCode      {@code vnp_ResponseCode} nguyên văn, {@code null} nếu không có response
 * @param transactionStatus {@code vnp_TransactionStatus} nguyên văn, {@code null} nếu VNPay không trả
 * @param payDate           {@code vnp_PayDate} nguyên văn ({@code yyyyMMddHHmmss} giờ GMT+7)
 * @param transactionNo     {@code vnp_TransactionNo} — mã giao dịch phía VNPay, dùng khi đối soát
 * @param bankCode          {@code vnp_BankCode} — ngân hàng khách dùng
 * @param amount            {@code vnp_Amount} nguyên văn (đã nhân 100), {@code null} nếu thiếu
 * @param message           mô tả ngắn để ghi log: do VNPay trả về, hoặc do chính hệ thống kết luận
 *                          khi không có response (timeout, sai chữ ký, lệch tiền…)
 */
public record VnpayQueryResult(
        VnpayQueryVerdict verdict,
        String responseCode,
        String transactionStatus,
        String payDate,
        String transactionNo,
        String bankCode,
        String amount,
        String message
) {

    /**
     * Kết quả khi <b>không hỏi được</b> VNPay: luôn là {@code UNKNOWN}.
     *
     * <p>Dựng sẵn ở đây để mọi nhánh lỗi (timeout, HTTP lỗi, body hỏng, chữ ký sai, lệch tiền…) đi
     * qua đúng một chỗ. Nếu mỗi nhánh tự dựng {@code new VnpayQueryResult(UNKNOWN, ...)} thì chỉ cần
     * một nhánh gõ nhầm thành {@code FAILED} là đủ để huỷ oan một đơn, và không test nào bắt được vì
     * nhánh đó hiếm khi chạy.</p>
     *
     * <p>{@code verdict} không phải tham số — đó chính là điểm mấu chốt: hàm này <b>không thể</b>
     * tạo ra kết quả cho phép huỷ.</p>
     */
    public static VnpayQueryResult unknown(String message) {
        return new VnpayQueryResult(
                VnpayQueryVerdict.UNKNOWN, null, null, null, null, null, null, message);
    }

    /** Kết quả khi có response nhưng chữ ký không hợp lệ, hoặc response không dùng được. */
    public static VnpayQueryResult unknownOf(VnpayQueryResult parsed, String message) {
        return new VnpayQueryResult(
                VnpayQueryVerdict.UNKNOWN,
                parsed == null ? null : parsed.responseCode(),
                parsed == null ? null : parsed.transactionStatus(),
                parsed == null ? null : parsed.payDate(),
                parsed == null ? null : parsed.transactionNo(),
                parsed == null ? null : parsed.bankCode(),
                parsed == null ? null : parsed.amount(),
                message);
    }
}
