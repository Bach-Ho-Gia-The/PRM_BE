package com.structure.springboot.features.payment.enums;

/**
 * Trạng thái hiển thị của trang kết quả thanh toán.
 *
 * <p><b>Vì sao là enum riêng chứ không lồng trong response:</b> golden standard của dự án đặt mọi
 * enum trong gói {@code enums} (xem {@code features/product/enums}, {@code features/order/enums}).
 * Enum lồng trong DTO là mẫu duy nhất không có tiền lệ — và nó còn khiến response mang hai khái
 * niệm cùng lúc: dữ liệu và cách hiển thị.</p>
 *
 * <p>Enum này thuộc về tầng hiển thị, không phải trạng thái lưu trong database. Đừng nhầm với
 * {@link PaymentStatus} — cái đó là dữ liệu, cái này là câu chữ khách đọc.</p>
 */
public enum VnpayReturnState {

    /** Đã trả tiền, đơn còn sống. */
    SUCCESS,

    /** Đã trả tiền nhưng đơn đã bị huỷ trước đó — sẽ được hoàn tiền. */
    PAID_AFTER_CANCEL,

    /** Cổng báo xong nhưng database chưa thấy tiền: IPN chưa về hoặc đã thất bại. */
    PENDING_CONFIRMATION,

    /** Cổng báo thất bại và database cũng không có tiền. */
    FAILED,

    /** Không tìm thấy giao dịch với {@code vnp_TxnRef} này. */
    TXN_REF_NOT_FOUND,

    /** Chữ ký sai: URL không do VNPay sinh ra. */
    INVALID_LINK,

    /**
     * Bản thân trang không dựng được (lỗi hệ thống).
     *
     * <p>Tách riêng thay vì dùng lại {@link #INVALID_LINK}: khi server lỗi mà báo "liên kết không
     * hợp lệ" là đổ lỗi cho khách và che mất sự cố thật. Khách vừa trả tiền xong.</p>
     */
    SYSTEM_ERROR
}
