package com.structure.springboot.features.product.repository;

import com.structure.springboot.features.product.enums.ProductSize;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, UUID> {

    /**
     * Lấy đúng một dòng tồn kho.
     *
     * <p>Dùng cái này thay vì duyệt {@code product.getVariants()}: nạp cả bộ sưu tập chỉ để lấy
     * một phần tử là thừa, và với đơn nhiều mặt hàng thì mỗi sản phẩm lại thêm một câu SQL nạp
     * toàn bộ variant của nó.</p>
     */
    Optional<ProductVariantEntity> findByProductIdAndSize(UUID productId, ProductSize size);
}
