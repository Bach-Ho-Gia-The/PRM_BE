package com.structure.springboot.features.collection.entity;

import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Một sản phẩm trong một BST. Dùng entity liên kết thay cho ManyToMany thuần để lưu thứ tự kéo thả;
 * thêm/xoá liên kết không được cascade sang sản phẩm.
 */
@Entity
@Table(
        name = "collection_products",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_collection_products_collection_product",
                columnNames = {"collection_id", "product_id"}
        ),
        indexes = {
                @Index(name = "idx_collection_products_sort", columnList = "collection_id, sort_order, product_id"),
                @Index(name = "idx_collection_products_product_id", columnList = "product_id")
        }
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CollectionProductEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private CollectionEntity collection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    /** Thứ tự hiển thị trong BST; logic kéo thả và kiểm tra danh sách thuộc task API. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
