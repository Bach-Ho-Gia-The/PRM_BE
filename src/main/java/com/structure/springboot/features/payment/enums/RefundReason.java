package com.structure.springboot.features.payment.enums;

/**
 * Vì sao phát sinh một yêu cầu hoàn tiền.
 *
 * <p><b>Cột {@code reason} trong bảng hoàn tiền sinh ra để thay cho một cột boolean
 * {@code settled_after_cancel} mà bản nháp định thêm.</b> Một chuỗi lý do vừa phân biệt được hai
 * ca, vừa nói được lý do thứ ba nếu sau này có, mà không phải thêm cột mới cho mỗi ca.</p>
 *
 * <p>Hai ca dưới đây <b>không thể gộp</b>, dù cùng dẫn tới một bảng: một ca khách chủ động huỷ đơn
 * đã trả tiền, ca kia khách huỷ xong mới trả tiền (VNPay báo về muộn). Shop xử lý hai ca này khác
 * nhau — ca thứ hai phải kiểm lại với cổng thanh toán trước khi chuyển tiền, vì khách có thể
 * không biết mình đã bị trừ.</p>
 */
public enum RefundReason {

    /**
     * Khách hoặc shop huỷ một đơn <b>đã thanh toán</b>.
     *
     * <p>Đường sinh ra: {@code OrderServiceImpl.markCancelled} — cửa duy nhất ghi {@code CANCELLED}.</p>
     */
    ORDER_CANCELLED,

    /**
     * Đơn đã bị huỷ (do hết hạn thanh toán hoặc khách huỷ) <b>rồi mới</b> có IPN báo đã trả tiền.
     *
     * <p>Đường sinh ra: nhánh {@code L16} trong xử lý IPN. Đây là ca khó nhất của toàn bộ luồng
     * thanh toán: khách đã trả tiền, đơn đã chết, hàng đã về kho. Tiền <b>phải</b> được ghi nhận dù
     * đơn không còn — không ghi là mất tiền của khách mà không ai biết.</p>
     */
    PAID_AFTER_CANCEL
}
