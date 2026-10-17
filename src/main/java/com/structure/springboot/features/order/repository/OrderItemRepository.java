package com.structure.springboot.features.order.repository;

import com.structure.springboot.features.order.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    /** A committed order snapshot keeps its image URL even when the Product changes. */
    boolean existsByImageUrl(String imageUrl);
}
