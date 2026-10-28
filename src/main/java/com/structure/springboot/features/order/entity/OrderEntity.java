package com.structure.springboot.features.order.entity;

import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn hàng.
 *
 * <p><b>Không kế thừa {@code SoftDeletableEntity}:</b> đơn hàng không bao giờ bị xoá, kể cả xoá
 * mềm — huỷ đơn là chuyển {@link OrderStatus#CANCELLED}, đó là một trạng thái nghiệp vụ hợp lệ
 * chứ không phải biến mất. Thêm một cột {@code status} thứ hai chỉ gây mơ hồ.</p>
 *
 * <p><b>Thông tin người nhận được chụp lại vào đơn</b>, không join sang {@code accounts}: khách
 * đổi địa chỉ trong hồ sơ thì đơn đã giao tháng trước vẫn phải hiện đúng địa chỉ lúc đó.</p>
 */
@Entity
@Table(
        name = "orders",
        // Postgres KHÔNG tự tạo index cho cột khoá ngoại. Thiếu index này thì màn "đơn hàng của
        // tôi" phải quét toàn bảng orders, càng nhiều khách đặt càng chậm.
        indexes = @Index(name = "idx_orders_account_id", columnList = "account_id")
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OrderEntity extends BaseEntity {

    /** Mã đơn khách đọc được, dạng {@code LBVN-2510-A3KD}. Khác UUID khoá chính. */
    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "receiver_name", nullable = false, length = 150)
    private String receiverName;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "note", length = 500)
    private String note;

    /** Lý do khách tự huỷ. Null với đơn do admin huỷ hoặc đơn chưa huỷ. */
    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    /**
     * Hạn thanh toán của <b>đơn</b> — nguồn chuẩn duy nhất, có ngay từ lúc đặt hàng.
     *
     * <p><b>Vì sao nằm ở đơn chứ không nằm trên từng lần thử thanh toán:</b> khi một lần thử thất
     * bại dứt điểm, khách được thử lại bằng một giao dịch mới — nhưng <b>mọi lần thử dùng chung
     * một hạn</b>. Nếu hạn chỉ sống trên bảng thanh toán thì mỗi lần thử lại sinh ra một hạn mới,
     * tức là mở đường gia hạn vô hạn chỉ bằng cách bấm "thanh toán lại".</p>
     *
     * <p>{@code null} = đơn không có hạn thanh toán (COD — trả tiền khi nhận hàng).</p>
     *
     * <p>Đặt <b>một lần</b> lúc tạo đơn và không có đường nào sửa — đó chính là cơ chế "mở lại
     * link thanh toán không tự gia hạn thêm".</p>
     */
    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    /**
     * Hàng của đơn này đã từng được trả về kho hay chưa — <b>cờ một chiều, bật rồi không tắt</b>.
     *
     * <p><b>Đây là chốt chặn ở tầng dữ liệu, không phải trạng thái tạm.</b> Bốn đường có thể huỷ
     * cùng một đơn (khách huỷ, shop huỷ, tác vụ quét hết hạn, IPN báo kết quả). Khoá bi quan trên
     * đơn chặn được hai đường <b>đang chạy đồng thời</b>, nhưng khoá chỉ sống trong một giao dịch —
     * đường thứ hai tới sau khi giao dịch đầu đã commit thì khoá đã nhả. Cờ này sống trong bảng nên
     * chặn được cả trường hợp đó.</p>
     *
     * <p><b>Tuyệt đối không "tắt cờ sau khi hoàn kho xong":</b> sự thật "đã hoàn kho" không bao giờ
     * đảo ngược. Tắt đi thì lần huỷ thứ hai (bấm lại do lỗi mạng, tab khác, IPN về muộn) lại thấy
     * "chưa hoàn" và cộng kho thêm lần nữa — đúng thứ cờ này sinh ra để chặn.</p>
     */
    @Builder.Default
    @Column(name = "stock_restored", nullable = false)
    private Boolean stockRestored = Boolean.FALSE;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /**
     * Tổng SỐ LƯỢNG sản phẩm trong đơn (mua 3 cái cùng mẫu thì tính là 3, không phải 1).
     *
     * <p>Lưu sẵn thay vì cộng từ {@code items} lúc trả về: màn hình "Đơn của tôi" cố ý KHÔNG nạp
     * {@code items} (quan hệ lazy), cộng tại chỗ sẽ bắn thêm một câu SQL cho mỗi đơn trong trang.
     * Con số này cố định ngay khi đặt nên không có nguy cơ lệch với dữ liệu thật.</p>
     */
    @Column(name = "total_items", nullable = false)
    private Integer totalItems;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    // Không có ORDER BY thì thứ tự dòng hàng do database quyết định, mỗi lần gọi có thể khác nhau.
    @OrderBy("productName ASC, size ASC")
    private List<OrderItemEntity> items = new ArrayList<>();
}
