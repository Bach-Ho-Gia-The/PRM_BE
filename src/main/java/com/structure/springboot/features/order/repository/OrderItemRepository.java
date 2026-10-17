package com.structure.springboot.features.order.repository;

import com.structure.springboot.features.order.entity.OrderItemEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.product.enums.ProductSize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    /**
     * Size này còn nằm trong đơn nào chưa kết thúc không.
     *
     * <p>Dùng trước khi xoá một dòng tồn kho: đơn PENDING/CONFIRMED/SHIPPING bị huỷ sẽ phải hoàn
     * kho về đúng dòng đó, xoá mất thì không hoàn vào đâu được.</p>
     */
    boolean existsByProductIdAndSizeAndOrderStatusIn(UUID productId, ProductSize size,
                                                     Collection<OrderStatus> statuses);

    /** A committed order snapshot keeps its image URL even when the Product changes. */
    boolean existsByImageUrl(String imageUrl);
}
