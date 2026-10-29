package com.structure.springboot.features.order.dto.response;

/**
 * Tóm tắt một lượt quét đơn hết hạn.
 *
 * <h2>Vì sao là bốn con số chứ không phải một danh sách</h2>
 *
 * <p>Tác vụ nền chạy mỗi phút. Ghi log chi tiết từng đơn là biến log thành một bảng dữ liệu thứ hai
 * không ai đọc, trong khi thứ người vận hành thật sự cần là một dòng: <i>"lượt này làm gì, có gì bất
 * thường không"</i>. Bốn số cộng lại đúng bằng số đơn đã kiểm tra, nên nhìn là biết ngay có khớp
 * không.</p>
 *
 * <p>Bất biến: {@code checked = cancelled + paid + unclear}. Đó là điều test khoá lại — một bản tóm
 * tắt tự mâu thuẫn còn tệ hơn không có bản tóm tắt.</p>
 *
 * @param checked   số đơn đã đưa vào lượt quét này
 * @param cancelled số đơn đã huỷ vì VNPay xác nhận thất bại hoặc không có giao dịch
 * @param paid      số đơn VNPay xác nhận đã thanh toán (không huỷ, chỉ ghi nhận)
 * @param unclear   số đơn chưa có kết luận rõ ràng — không đụng gì, hẹn lượt sau
 */
public record OrderExpirySummary(
        int checked,
        int cancelled,
        int paid,
        int unclear
) {

    /** Lượt quét không có đơn nào. */
    public static OrderExpirySummary empty() {
        return new OrderExpirySummary(0, 0, 0, 0);
    }

    /** Bất biến {@code checked = cancelled + paid + unclear}. Xem javadoc ở đầu record. */
    public boolean isConsistent() {
        return checked == cancelled + paid + unclear;
    }

    /** Dòng log tóm tắt. Viết ở đây để mọi lượt quét log cùng một định dạng. */
    public String toLogLine() {
        return "order expiry scan: checked=" + checked
                + ", cancelled=" + cancelled
                + ", paid=" + paid
                + ", unclear=" + unclear;
    }
}
