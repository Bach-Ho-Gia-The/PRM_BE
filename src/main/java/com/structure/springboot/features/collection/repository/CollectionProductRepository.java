package com.structure.springboot.features.collection.repository;

import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CollectionProductRepository extends JpaRepository<CollectionProductEntity, UUID> {

    /** Chi tiết admin cần Product/status của từng liên kết: fetch to-one theo một câu SQL, tránh N+1. */
    @EntityGraph(attributePaths = {"product"})
    List<CollectionProductEntity> findByCollectionIdOrderBySortOrderAscIdAsc(UUID collectionId);

    /** Chỉ nạp các hàng liên kết để xoá hàng loạt khi xoá BST; không fetch Product. */
    List<CollectionProductEntity> findByCollectionId(UUID collectionId);

    boolean existsByCollectionIdAndProductId(UUID collectionId, UUID productId);

    /** Đếm tất cả liên kết; không dùng số này thay cho kiểm tra Product ACTIVE khi đăng BST. */
    long countByCollectionId(UUID collectionId);
}
