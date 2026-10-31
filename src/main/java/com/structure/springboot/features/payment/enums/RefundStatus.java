package com.structure.springboot.features.payment.enums;

/**
 * Trạng thái của một yêu cầu hoàn tiền.
 *
 * <p><b>Ba giá trị này khác nhau ở đúng một điểm quan trọng:</b> {@link #PENDING} nghĩa là
 * "cần hoàn" — <b>không</b> phải "đã hoàn". Đánh đồng hai chuyện đó là nói dối khách: họ thấy
 * đơn huỷ mà tiền vẫn chưa về, trong khi hệ thống ghi như đã xong.</p>
 *
 * <p><b>Đợt này chỉ ghi ra {@link #PENDING}.</b> {@link #REFUNDED} và {@link #REJECTED} cần thao
 * tác của shop nên thuộc đợt sau (cùng endpoint admin). Khai đủ ba ngay từ đầu để cột
 * {@code status} không phải đổi kiểu về sau, và để người đọc thấy trước vòng đời của nó.</p>
 */
public enum RefundStatus {

    /** Đã ghi nhận là phải hoàn, <b>chưa</b> chuyển tiền. Trạng thái duy nhất đợt này sinh ra. */
    PENDING,

    /** Shop đã chuyển tiền hoàn cho khách. */
    REFUNDED,

    /** Shop từ chối hoàn (ví dụ khách báo nhầm, hoặc tiền chưa thực sự bị trừ). */
    REJECTED
}
