package com.structure.springboot.features.order.entity;

import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.enums.ProductSize;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Một dòng hàng trong đơn.
 *
 * <p><b>Vì sao chép lại {@code productName}, {@code unitPrice}, {@code imageUrl} thay vì join
 * sang bảng sản phẩm:</b> sang tháng shop tăng giá hoặc đổi ảnh thì hoá đơn cũ vẫn phải hiện
 * đúng những gì khách đã thấy và đã trả. Join sống là lịch sử bị viết lại.</p>
 *
 * <p>Vẫn giữ khoá ngoại {@code product_id} để thống kê "sản phẩm nào bán chạy" — và đây chính
 * là lý do sản phẩm phải xoá mềm, xoá cứng là đứt khoá ngoại này.</p>
 */
@Entity
@Table(
        name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order_id", columnList = "order_id"),
                @Index(name = "idx_order_items_image_url", columnList = "image_url")
        }
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_slug", length = 255)
    private String productSlug;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false, length = 10)
    private ProductSize size;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** {@code unitPrice × quantity}, lưu sẵn để báo cáo khỏi phải nhân lại ở mọi câu truy vấn. */
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
}
