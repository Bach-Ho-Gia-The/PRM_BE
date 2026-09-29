package com.structure.springboot.features.product.repository;

import com.structure.springboot.features.product.entity.ProductEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);

    // Tự động JOIN category khi tìm theo ID -> 1 câu SQL, không bị LazyInitException
    @Override
    @EntityGraph(attributePaths = {"category"})
    Optional<ProductEntity> findById(UUID id);

    // Tự động JOIN category khi lấy tất cả -> Chống hoàn toàn lỗi N+1 Query
    @Override
    @EntityGraph(attributePaths = {"category"})
    List<ProductEntity> findAll();
}
