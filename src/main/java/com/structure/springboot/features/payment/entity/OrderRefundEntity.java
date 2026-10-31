package com.structure.springboot.features.payment.entity;

import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.enums.RefundStatus;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Một khoản <b>cần hoàn</b> cho khách — bảng riêng, không phải cột trên {@code payments}.
 *
 * <h2>Vì sao tách bảng thay vì thêm cột {@code refund_needed} vào {@code payments}</h2>
 *
 * <p>Hoàn tiền có <b>vòng đời riêng</b>: phát sinh → shop chuyển tiền → xong. Ba bước đó không
 * đồng bộ với vòng đời thanh toán, và shop còn cần ghi chú, thời điểm xử lý, người xử lý. Nhét
 * vào {@code payments} thì mỗi lần thêm thông tin hoàn tiền lại phải thêm cột vào bảng thanh
 * toán — bảng vốn đã có 14 cột, và ý nghĩa các cột sẽ lẫn lộn (cột nào của thanh toán, cột nào
 * của hoàn tiền).</p>
 *
 * <h2>{@code order_id} UNIQUE — chốt chặn ở tầng dữ liệu</h2>
 *
 * <p>Một đơn chỉ có tối đa <b>một</b> khoản hoàn. Đây không phải ràng buộc hình thức: bốn đường
 * cùng có thể phát sinh hoàn tiền cho một đơn (khách huỷ, shop huỷ, IPN về muộn, IPN gửi lặp).
 * Khoá bi quan trên đơn chặn được các đường <b>chạy đồng thời</b>, nhưng khoá chỉ sống trong một
 * giao dịch — đường tới sau khi giao dịch trước đã commit thì khoá đã nhả. Ràng buộc unique sống
 * trong bảng nên chặn được cả trường hợp đó, đúng vai trò mà cờ {@code stock_restored} đảm nhiệm
 * bên phía tồn kho.</p>
 *
 * <p><b>Hệ quả khi ghi:</b> mọi đường ghi phải <b>kiểm trước</b> ({@code existsByOrderId}) rồi mới
 * {@code save}. Kiểm trước trong cùng giao dịch đã có khoá đơn là đủ; ràng buộc unique là lớp thứ
 * hai phòng khi có đường mới quên kiểm.</p>
 *
 * <h2>{@code amount} lấy từ {@code payments.amount}, KHÔNG phải {@code orders.total}</h2>
 *
 * <p>Đây là số tiền <b>thực sự đã thu</b>. Hai con số này có thể lệch nhau: phí ship được tính
 * lại, mã giảm giá thêm sau, hoặc đơn được sửa. Hoàn theo {@code orders.total} thì hoặc hoàn
 * thiếu (khách mất tiền) hoặc hoàn thừa (shop mất tiền) — cả hai đều không ai phát hiện được cho
 * tới khi đối soát.</p>
 */
@Entity
@Table(
        name = "order_refunds",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_refund_order",
                columnNames = "order_id"
        )
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OrderRefundEntity extends BaseEntity {

    /**
     * Đơn cần hoàn tiền. Quan hệ một chiều sang {@code orders}, giống {@code PaymentEntity} — phía
     * đơn hàng không biết gì về hoàn tiền, đúng chiều phụ thuộc đã chốt.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private OrderEntity order;

    /**
     * Bản ghi thanh toán đã thu được tiền.
     *
     * <p>Giữ tham chiếu tới đúng lần thử đã trả tiền, không phải "lần thử mới nhất": một đơn có
     * thể có nhiều lần thử, và chỉ một trong số đó thu được tiền. Ghi theo "lần mới nhất" thì khi
     * khách thử lại sau khi đã trả (không xảy ra bình thường, nhưng dữ liệu cũ thì có) con trỏ sẽ
     * trỏ sai bản ghi.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private PaymentEntity payment;

    /** Số tiền phải hoàn — chép từ {@code payments.amount}, xem javadoc cấp lớp. */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * Mặc định {@code PENDING}: một khoản vừa phát sinh thì <b>chưa</b> hoàn.
     *
     * <p>Đặt mặc định ở đây chứ không bắt người gọi truyền vào: mọi đường sinh hoàn tiền trong
     * đợt này đều bắt đầu từ {@code PENDING}, bắt truyền chỉ tạo cơ hội truyền sai.</p>
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status = RefundStatus.PENDING;

    /**
     * Lý do phát sinh. Xem {@link RefundReason} để biết vì sao cần phân biệt thay vì dùng boolean.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private RefundReason reason;
}
