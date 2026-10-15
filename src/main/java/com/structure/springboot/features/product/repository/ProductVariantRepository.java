package com.structure.springboot.features.product.repository;

import com.structure.springboot.features.product.entity.ProductVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, UUID> {

    /**
     * Nạp toàn bộ dòng tồn kho của nhiều sản phẩm trong một câu SQL.
     *
     * <p>Dành cho lúc đặt hàng: tra từng cặp (sản phẩm, size) trong vòng lặp thì giỏ bao nhiêu
     * món là bấy nhiêu câu SQL. Lấy một lượt rồi tra trong bộ nhớ thì số câu SQL không còn phụ
     * thuộc vào kích thước giỏ.</p>
     */
    List<ProductVariantEntity> findByProductIdIn(Collection<UUID> productIds);
}
