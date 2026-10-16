package com.structure.springboot.features.collection.repository;

import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CollectionProductRepository extends JpaRepository<CollectionProductEntity, UUID> {

    boolean existsByCollectionIdAndProductId(UUID collectionId, UUID productId);

    /** Đếm tất cả liên kết; không dùng số này thay cho kiểm tra Product ACTIVE khi đăng BST. */
    long countByCollectionId(UUID collectionId);
}
