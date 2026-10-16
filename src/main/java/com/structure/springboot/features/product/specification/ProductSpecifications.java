package com.structure.springboot.features.product.specification;

import com.structure.springboot.features.product.enums.ProductCollection;
import com.structure.springboot.features.product.entity.ProductEntity;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Bộ lọc danh sách sản phẩm cho trang danh mục.
 *
 * <p>Dùng Specification thay vì đẻ ra hàng loạt Derived Query (
 * {@code findByCategorySlugAndPriceBetweenAndNameContaining...}): năm bộ lọc đều tuỳ chọn nên số
 * tổ hợp là 2^5, không thể viết tay từng method. Specification ghép đúng những điều kiện client
 * thực sự gửi lên.</p>
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

    /** Lọc theo slug danh mục ({@code ?category=ao}) — FE không cầm UUID của danh mục. */
    public static Specification<ProductEntity> hasCategorySlug(String categorySlug) {
        if (!StringUtils.hasText(categorySlug)) {
            return Specification.unrestricted();
        }
        String normalized = categorySlug.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category").get("slug")), normalized);
    }

    public static Specification<ProductEntity> hasCollection(ProductCollection collection) {
        return collection == null
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("collection"), collection);
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

    public static Specification<ProductEntity> isFeatured(Boolean featured) {
        return featured == null
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("isFeatured"), featured);
    }
}
