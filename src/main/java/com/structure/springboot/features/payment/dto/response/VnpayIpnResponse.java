package com.structure.springboot.features.payment.dto.response;

import com.structure.springboot.features.payment.entity.OrderRefundEntity;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.enums.RefundReason;

import java.math.BigDecimal;

/**
 * Ảnh chụp trạng thái thanh toán tại thời điểm IPN được xử lý.
 *
 * <h2>Vì sao cần kiểu trả về này thay vì trả {@code PaymentEntity}</h2>
 *
 * <p>Endpoint IPN phải trả về một {@code RspCode} cho VNPay, và mã đó phụ thuộc vào <b>trạng
 * thái TRƯỚC khi xử lý</b>, không chỉ trạng thái sau. Ví dụ: IPN đến lần thứ hai cho một payment
 * đã {@code PAID} phải trả {@code 02} (đã cập nhật rồi), không phải {@code 00} (vừa xác nhận).
 * Muốn quyết định đúng thì lớp xử lý phải mang cả hai thông tin ra ngoài.</p>
 *
 * <p>Trả entity ra khỏi ranh giới giao dịch cũng là điều không nên: entity gắn với persistence
 * context, ra ngoài transaction thì quan hệ lazy ném
 * {@code LazyInitializationException}. Record này chỉ chứa dữ liệu phẳng.</p>
 *
 * @param rspCode     mã trả cho VNPay: {@code 00}, {@code 01}, {@code 02}, {@code 04}, {@code 97},
 *                    {@code 99}
 * @param message     câu đi kèm, VNPay hiển thị trong portal của họ
 * @param previousStatus trạng thái payment <b>trước</b> khi xử lý IPN này; {@code null} nếu không
 *                    tìm thấy payment — đúng những ca trả {@code 01}
 * @param refundRequested khoản hoàn vừa được ghi nhận (ca {@code L16}: đơn đã huỷ mà tiền về muộn);
 *                    {@code null} ở mọi ca khác
 */
public record VnpayIpnResponse(
        String rspCode,
        String message,
        PaymentStatus previousStatus,
        RefundSummary refundRequested
) {

    /**
     * Thông tin tối thiểu của một khoản hoàn vừa ghi — chỉ để lớp gọi log ra.
     *
     * <p>Không trả cả {@link OrderRefundEntity}: record này đi ra khỏi transaction.</p>
     */
    public record RefundSummary(BigDecimal amount, RefundReason reason) {

        public static RefundSummary of(OrderRefundEntity refund) {
            return new RefundSummary(refund.getAmount(), refund.getReason());
        }
    }

    // ── Các mã VNPay quy định. Đặt thành hằng để không gõ chuỗi rải rác trong service. ──

    /** Đã ghi nhận. VNPay <b>dừng</b> gọi lại. */
    public static final String RSP_SUCCESS = "00";
    /** Đơn không tồn tại. VNPay <b>gọi lại</b>. */
    public static final String RSP_ORDER_NOT_FOUND = "01";
    /** Đã cập nhật trước đó rồi. VNPay <b>dừng</b> gọi lại. */
    public static final String RSP_ALREADY_CONFIRMED = "02";
    /** Số tiền không khớp. VNPay <b>gọi lại</b>. */
    public static final String RSP_INVALID_AMOUNT = "04";
    /** Sai chữ ký. VNPay <b>gọi lại</b>. */
    public static final String RSP_INVALID_SIGNATURE = "97";
    /** Lỗi không xác định. VNPay <b>gọi lại</b>. */
    public static final String RSP_UNKNOWN_ERROR = "99";

    public static VnpayIpnResponse of(String rspCode, String message, PaymentStatus previousStatus) {
        return new VnpayIpnResponse(rspCode, message, previousStatus, null);
    }

    public static VnpayIpnResponse success(PaymentStatus previousStatus) {
        return of(RSP_SUCCESS, "Confirm Success", previousStatus);
    }

    public static VnpayIpnResponse alreadyConfirmed(PaymentStatus previousStatus) {
        return of(RSP_ALREADY_CONFIRMED, "Order already confirmed", previousStatus);
    }

    /**
     * Thanh toán về muộn cho một đơn đã chết — vẫn ghi nhận, kèm khoản hoàn.
     *
     * <p>Trả {@code 00} chứ <b>không</b> trả lỗi: ta đã ghi nhận đúng sự việc, bảo VNPay gọi lại
     * 10 lần nữa cũng không thay đổi được gì, chỉ tốn tài nguyên hai bên.</p>
     */
    public static VnpayIpnResponse paidAfterCancel(PaymentStatus previousStatus, OrderRefundEntity refund) {
        return new VnpayIpnResponse(
                RSP_SUCCESS,
                "Confirm Success - paid after cancellation, refund recorded",
                previousStatus,
                RefundSummary.of(refund));
    }
}
