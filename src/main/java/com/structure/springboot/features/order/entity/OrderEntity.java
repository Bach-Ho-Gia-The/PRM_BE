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
