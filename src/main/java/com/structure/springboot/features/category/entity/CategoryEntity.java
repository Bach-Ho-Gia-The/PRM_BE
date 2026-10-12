package com.structure.springboot.features.category.entity;

import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.infrastructure.persistence.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryEntity extends SoftDeletableEntity {

    @Column(name = "category_name", nullable = false, unique = true, length = 100)
    private String categoryName;

    /**
     * Định danh công khai dùng trên URL và query param ({@code /bo-suu-tap/ao}, {@code ?category=ao}).
     * Nullable ở tầng DB vì {@code ddl-auto: update}, nhưng service luôn sinh giá trị.
     */
    @Column(name = "slug", unique = true, length = 100)
    private String slug;

    /**
     * CHỈ để đọc ngược. KHÔNG cascade, KHÔNG orphanRemoval: xoá một Category không được phép
     * kéo theo xoá sản phẩm. Việc chặn xoá do service kiểm tra.
     */
    @Builder.Default
    @OneToMany(mappedBy = "category")
    private List<ProductEntity> products = new ArrayList<>();
}
