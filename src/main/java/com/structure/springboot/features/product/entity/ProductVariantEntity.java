package com.structure.springboot.features.product.entity;

import com.structure.springboot.features.product.enums.ProductSize;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Tồn kho theo từng size của một sản phẩm.
 *
 * <p><b>Vì sao tách bảng thay vì để {@code stockQuantity} trên sản phẩm:</b> shop thời trang hết
 * size M trong khi S và L vẫn còn là chuyện thường ngày. Một con số tổng không trả lời được câu
 * hỏi "size này còn hàng không", mà đó đúng là câu khách hỏi trước khi bấm mua.</p>
 */
@Entity
@Table(
        name = "product_variants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_variant_size",
                columnNames = {"product_id", "size"}
        )
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariantEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false, length = 10)
    private ProductSize size;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    /**
     * Khoá lạc quan chống bán quá hàng.
     *
     * <p>Hai khách cùng đặt chiếc áo size M cuối cùng trong một tích tắc: cả hai đều đọc ra
     * {@code stock = 1}, cả hai đều thấy đủ hàng, cả hai cùng ghi {@code stock = 0} — shop bán
     * mất hai cái mà chỉ có một. Với {@code @Version}, giao dịch ghi sau sẽ thấy số phiên bản đã
     * đổi và bị từ chối, Spring ném {@code OptimisticLockingFailureException} → trả 409.</p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
