package com.structure.springboot.features.product.entity;

import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.infrastructure.persistence.SoftDeletableEntity;
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

@Entity
@Table(
        name = "products",
        // Postgres KHÔNG tự tạo index cho cột khoá ngoại. Thiếu idx category_id thì lọc theo danh
        // mục và lệnh xoá danh mục đều quét toàn bảng products. Cùng khuôn với collection_products.
        indexes = {
                @Index(name = "idx_products_category_id", columnList = "category_id"),
                @Index(name = "idx_products_status", columnList = "status")
        }
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ProductEntity extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Định danh công khai dùng trên URL ({@code /san-pham/{slug}}).
     *
     * <p>Để nullable ở tầng DB vì {@code ddl-auto: update} không thêm được cột NOT NULL vào bảng
     * đã có dữ liệu; tầng service luôn sinh giá trị nên trên thực tế không bao giờ null.
     * Giữ {@code unique} để DB là chốt chặn cuối, tránh hai sản phẩm cùng slug do hai request
     * tạo song song lọt qua bước kiểm tra tồn tại.</p>
     */
    @Column(name = "slug", unique = true, length = 255)
    private String slug;

    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Giá gốc để gạch ngang và tính phần trăm giảm. Null = không giảm giá. */
    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "material", columnDefinition = "TEXT")
    private String material;

    @Column(name = "care_instructions", columnDefinition = "TEXT")
    private String careInstructions;

    /** Nhãn hiển thị trên thẻ sản phẩm: "Mới", "Hot", "Signature". Null = không có nhãn. */
    @Column(name = "tag", length = 50)
    private String tag;

    /** Ảnh đại diện hiển thị ở lưới danh sách. Gallery đầy đủ nằm ở {@link ProductImageEntity}. */
    @Column(name = "thumbnail", length = 500)
    private String thumbnail;

    /** Null for seed/external thumbnails; for managed images this matches the selected gallery image. */
    @Column(name = "thumbnail_public_id", length = 500)
    private String thumbnailPublicId;

    @Builder.Default
    @Column(name = "is_summer")
    private Boolean isSummer = false;

    @Builder.Default
    @Column(name = "is_featured")
    private Boolean isFeatured = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    /**
     * {@code orphanRemoval} để sửa sản phẩm bằng cách thay cả danh sách variant: variant bị bỏ ra
     * khỏi list là bị xoá khỏi DB luôn, không cần gọi repository riêng.
     */
    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariantEntity> variants = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductImageEntity> images = new ArrayList<>();

    /**
     * Sản phẩm có đang được bán không. Hàng dữ liệu cũ chưa có cột status (null) vẫn tính là đang
     * bán — cùng quy ước với {@code StatusSpecifications.visible()} dùng ở tầng truy vấn.
     *
     * <p>Để ở entity vì Product, Collection và Order đều cần đúng một định nghĩa này; trước đây
     * mỗi service chép một bản, sửa quy ước ở một chỗ là ba nơi lệch nhau.</p>
     */
    public boolean isVisible() {
        return getStatus() == null || getStatus() == RecordStatus.ACTIVE;
    }
}
