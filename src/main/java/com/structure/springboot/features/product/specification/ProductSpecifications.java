package com.structure.springboot.features.product.specification;

import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Bộ lọc danh sách sản phẩm cho trang danh mục.
 *
 * <p>Ghép các điều kiện tuỳ chọn bằng Specification: lọc ở DB trước COUNT/phân trang,
 * không tạo một Query Method cho mỗi tổ hợp filter.</p>
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /**
     * JOIN FETCH category ngay trong câu lọc để tránh N+1 khi map sang DTO.
     *
     * <p><b>Bắt buộc phải bỏ qua khi Spring chạy câu đếm:</b> {@code findAll(spec, pageable)} chạy
     * hai câu — một câu lấy dữ liệu và một câu {@code COUNT}. {@code COUNT} mà kèm fetch join sẽ
     * văng lỗi vì không được phép fetch trong truy vấn trả về số vô hướng.</p>
     */
    public static Specification<ProductEntity> fetchCategory() {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("category", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    /** Chỉ khi KHÁCH chọn Category, slug HIDDEN/null mới trả trang rỗng. */
    public static Specification<ProductEntity> hasCategorySlug(String categorySlug) {
        if (!StringUtils.hasText(categorySlug)) {
            return Specification.unrestricted();
        }
        String normalized = categorySlug.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.and(
                cb.equal(cb.lower(root.get("category").get("slug")), normalized),
                cb.equal(root.get("category").get("status"), RecordStatus.ACTIVE)
        );
    }

    /**
     * Lọc theo BST thật đã publish, không theo nhãn cũ trên Product. EXISTS tránh nhân bản
     * Product khi liên kết nhiều BST, giữ COUNT/paging đúng; Category HIDDEN không cản trở.
     */
    public static Specification<ProductEntity> inPublishedCollection(String collectionSlug) {
        if (collectionSlug == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> {
            Subquery<Integer> membership = query.subquery(Integer.class);
            Root<CollectionProductEntity> link = membership.from(CollectionProductEntity.class);
            membership.select(cb.literal(1)).where(
                    cb.equal(link.get("product").get("id"), root.get("id")),
                    cb.equal(link.get("collection").get("slug"), collectionSlug),
                    cb.equal(link.get("collection").get("status"), RecordStatus.ACTIVE)
            );
            return cb.exists(membership);
        };
    }

    /**
     * Tìm theo tên hoặc nhãn. Hạ chữ thường cả hai vế vì mặc định Postgres so khớp
     * {@code LIKE} có phân biệt hoa thường — không có bước này thì gõ "sunset" sẽ không ra "Sunset".
     */
    public static Specification<ProductEntity> matchesKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Specification.unrestricted();
        }
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("tag"), "")), pattern)
        );
    }

    public static Specification<ProductEntity> priceAtLeast(BigDecimal minPrice) {
        return minPrice == null
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<ProductEntity> priceAtMost(BigDecimal maxPrice) {
        return maxPrice == null
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    /**
     * Mọi Product trỏ tới một Category, bất kể trạng thái. Dùng cho lệnh xoá Category: ghép với
     * {@code StatusSpecifications.notDeleted()} để hỏi DB bằng EXISTS thay vì tải danh sách Product.
     */
    public static Specification<ProductEntity> inCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<ProductEntity> isFeatured(Boolean featured) {
        return featured == null
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("isFeatured"), featured);
    }
}
