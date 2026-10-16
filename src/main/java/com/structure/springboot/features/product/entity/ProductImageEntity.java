package com.structure.springboot.features.product.entity;

import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Một ảnh trong gallery của sản phẩm.
 *
 * <p>Hiện chỉ lưu URL dạng chuỗi — chưa có chỗ chứa file nên ảnh trỏ thẳng ra nguồn ngoài. Khi nào
 * chốt được nơi lưu trữ thì chỉ cần thêm một endpoint upload trả về URL rồi ghi vào đúng cột này,
 * KHÔNG phải sửa bảng.</p>
 */
@Entity
@Table(name = "product_images")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ProductImageEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** Thứ tự hiển thị trong gallery. Nhỏ hơn đứng trước; trùng số thì thứ tự không đảm bảo. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
