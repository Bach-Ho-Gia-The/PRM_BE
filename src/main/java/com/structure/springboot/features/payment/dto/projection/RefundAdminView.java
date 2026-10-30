package com.structure.springboot.features.payment.dto.projection;

import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection <b>đóng</b> cho màn danh sách hoàn tiền của shop — chỉ những cột màn hình cần.
 *
 * <h2>Vì sao projection chứ không trả {@code OrderRefundEntity} rồi map</h2>
 *
 * <p>Mỗi khoản hoàn gắn với một đơn ({@code order}) ở quan hệ lazy. Trả entity rồi đọc
 * {@code refund.getOrder().getOrderNumber()} trong lúc map là <b>N+1</b>: một câu SQL cho danh sách
 * khoản hoàn, rồi mỗi dòng thêm một câu nạp đơn. Projection đóng bắt Spring Data sinh <b>một</b> câu
 * có sẵn {@code join orders} và chỉ {@code select} đúng các cột khai dưới đây.</p>
 *
 * <p><b>Đóng, không mở:</b> mọi getter trỏ thẳng tới thuộc tính (kể cả {@link OrderRef} lồng), không
 * dùng {@code @Value}/SpEL. Projection mở sẽ buộc Hibernate nạp cả entity gốc, đúng thứ đang tránh.</p>
 *
 * <p><b>Tên khách lấy từ {@code order.receiverName}</b>, không join sang {@code accounts}: đơn đã
 * chụp lại thông tin người nhận lúc đặt, nên không cần chạm bảng tài khoản (và cũng đúng tên lúc
 * đặt đơn, không phải tên hồ sơ hiện tại).</p>
 */
public interface RefundAdminView {

    /** Mã khoản hoàn — khoá chính của {@code order_refunds}. */
    UUID getId();

    BigDecimal getAmount();

    RefundReason getReason();

    RefundStatus getStatus();

    Instant getCreatedAt();

    /** Đơn gắn với khoản hoàn — projection lồng để Spring Data chỉ nạp hai cột cần, không cả đơn. */
    OrderRef getOrder();

    interface OrderRef {
        String getOrderNumber();

        String getReceiverName();
    }
}
