package com.structure.springboot.features.product.repository;

import com.structure.springboot.features.product.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code JpaSpecificationExecutor} để trang danh mục ghép được các bộ lọc tuỳ chọn
 * (xem {@code ProductSpecifications}); phần còn lại vẫn là Derived Query theo chuẩn dự án.
 */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID>,
        JpaSpecificationExecutor<ProductEntity> {

    boolean existsBySku(String sku);
    Optional<ProductEntity> findBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);

    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);

    // Chặn xoá Category khi vẫn còn sản phẩm trỏ tới -> thay cho cascade đã gỡ bỏ

    /** Serialize product image edits and metadata/status updates with checkout image snapshots. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductEntity> findWithLockById(UUID id);

    /** Checkout locks Product rows in a stable order until the order snapshot commits. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<ProductEntity> findByIdInOrderByIdAsc(java.util.Collection<UUID> ids);

    // Tự động JOIN category khi tìm theo ID -> 1 câu SQL, không bị LazyInitException
    @Override
    @EntityGraph(attributePaths = {"category"})
    Optional<ProductEntity> findById(UUID id);

    // Đường vào chính của trang chi tiết phía khách: /san-pham/{slug}
    @EntityGraph(attributePaths = {"category"})
    Optional<ProductEntity> findBySlug(String slug);

    // Tự động JOIN category khi lấy tất cả -> Chống hoàn toàn lỗi N+1 Query
    @Override
    @EntityGraph(attributePaths = {"category"})
    List<ProductEntity> findAll();

    // Bản phân trang là một method KHÁC, không kế thừa @EntityGraph của bản List ở trên.
    // Thiếu dòng override này thì mỗi trang 20 sản phẩm sẽ bắn thêm 20 câu SELECT category.
    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<ProductEntity> findAll(Pageable pageable);
}
