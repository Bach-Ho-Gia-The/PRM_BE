package com.structure.springboot.features.payment.enums;

/**
 * Bốn kết luận có thể rút ra sau khi hỏi VNPay về một giao dịch.
 *
 * <h2>Đây là toàn bộ lý do lệnh {@code querydr} tồn tại</h2>
 *
 * <p>Đơn quá hạn thanh toán mà vẫn {@code PENDING} có hai khả năng trái ngược: khách <b>chưa</b> trả
 * tiền, hoặc khách <b>đã</b> trả mà IPN chưa về. Nhìn database không phân biệt được. Huỷ nhầm ca thứ
 * hai nghĩa là khách mất tiền và mất luôn hàng.</p>
 *
 * <p>Nên kết quả trả về <b>không phải</b> câu trả lời hai giá trị. Nó là bốn, và sự khác biệt giữa
 * chúng là toàn bộ sự an toàn của hệ thống.</p>
 *
 * <h2>Quy tắc bất di bất dịch</h2>
 *
 * <p><b>Chỉ {@link #FAILED} và {@link #NOT_FOUND} được phép dẫn tới huỷ đơn.</b> Mọi trạng thái khác
 * của thế giới — kể cả những trạng thái mã không có trong tài liệu, kể cả khi không hỏi được VNPay —
 * đều rơi vào {@link #UNKNOWN}, và {@code UNKNOWN} <b>không</b> cho huỷ.</p>
 *
 * <p><b>{@code UNKNOWN} không phải {@code FAILED}.</b> Chúng chỉ giống nhau ở chỗ "không phải
 * {@code PAID}", và đó chính là cái bẫy: người viết code sau dễ đọc {@code UNKNOWN} thành "không
 * thành công" rồi huỷ. Hai trạng thái này có <b>hệ quả trái ngược</b> và vì thế là hai hằng số khác
 * nhau chứ không phải một hằng số với ghi chú.</p>
 */
public enum VnpayQueryVerdict {

    /**
     * VNPay xác nhận giao dịch <b>đã thu tiền thành công</b>.
     *
     * <p>Dấu hiệu: {@code vnp_ResponseCode = "00"} và {@code vnp_TransactionStatus = "00"}.</p>
     */
    PAID,

    /**
     * VNPay xác nhận giao dịch <b>thất bại</b> — tiền chưa hề rời tài khoản khách.
     *
     * <p>Dấu hiệu: {@code vnp_ResponseCode = "00"} và {@code vnp_TransactionStatus = "02"}.</p>
     *
     * <p><b>Vì sao chỉ {@code 02}:</b> các mã như {@code 01} (chưa hoàn tất), {@code 04} (giao dịch
     * đảo), {@code 05}/{@code 06} (hoàn tiền) đều <i>không</i> có nghĩa là "chưa trả tiền". Đảo và
     * hoàn tiền là tiền <b>đã chạy rồi chạy ngược</b> — gộp chúng vào đây là huỷ đơn của một người
     * đang chờ tiền hoàn về.</p>
     */
    FAILED,

    /**
     * VNPay khẳng định <b>không có giao dịch nào</b> với mã này.
     *
     * <p>Dấu hiệu: {@code vnp_ResponseCode = "91"}.</p>
     *
     * <p>Đây là câu trả lời mạnh hơn {@code FAILED}: không phải "giao dịch hỏng" mà "giao dịch không
     * tồn tại" — khách chưa từng vào cổng, hoặc bấm huỷ từ màn hình VNPay.</p>
     */
    NOT_FOUND,

    /**
     * <b>Không kết luận được gì.</b> Không được hành động dựa trên trạng thái này.
     *
     * <p>Bao gồm, và đây là danh sách <i>không</i> đầy đủ — mọi thứ không rõ ràng đều rơi vào đây:
     * không gọi được VNPay, timeout, HTTP lỗi, response không parse được, chữ ký sai (kể cả khi
     * {@code vnp_ResponseCode = "00"}), lệch {@code vnp_TxnRef}, lệch số tiền, và mọi mã VNPay
     * không nằm trong bảng đã chốt.</p>
     *
     * <p>Hành động đúng với {@code UNKNOWN} là <b>hẹn kiểm tra lại sau</b>: để nguyên đơn, nguyên
     * kho, nguyên payment.</p>
     */
    UNKNOWN;

    /**
     * Trạng thái này có đủ căn cứ để huỷ đơn và hoàn kho hay không.
     *
     * <p>Viết thành hàm trên enum chứ không để người gọi tự viết
     * {@code if (verdict == FAILED || verdict == NOT_FOUND)}: điều kiện này xuất hiện ở nhiều chỗ
     * (tác vụ quét, test, log) và mỗi chỗ tự viết là một chỗ có thể viết thiếu một vế. Ở đây chỉ có
     * <b>một</b> định nghĩa của câu "được phép huỷ".</p>
     *
     * <p>Cố ý viết dạng khẳng định (liệt kê hai giá trị <i>được</i> phép) chứ không dạng phủ định
     * ({@code != PAID && != UNKNOWN}): thêm một trạng thái mới vào enum này sẽ mặc định <b>không</b>
     * được phép huỷ, thay vì mặc định được phép. Sai theo hướng không huỷ là sai sửa được; sai theo
     * hướng huỷ nhầm thì không.</p>
     */
    public boolean allowsCancel() {
        return this == FAILED || this == NOT_FOUND;
    }
}
