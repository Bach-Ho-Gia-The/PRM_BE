package com.structure.springboot.features.collection.specification;

import com.structure.springboot.features.collection.entity.CollectionEntity;
import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/** Các điều kiện truy vấn BST được áp dụng trong DB trước khi phân trang. */
public final class CollectionSpecifications {

    private CollectionSpecifications() {
    }

    /**
     * BST phải được publish rõ ràng (ACTIVE), và còn ít nhất một Product công khai.
     * Product.status null từ dữ liệu cũ vẫn được coi là ACTIVE; Collection.status null thì không:
     * BST chưa được publish không được tự lộ ra cửa hàng.
     *
     * <p>Dùng EXISTS thay cho JOIN bảng liên kết vào trang BST: không nhân bản dòng BST,
     * totalElements vẫn đúng, kể cả một BST chứa nhiều sản phẩm.</p>
     */
    public static Specification<CollectionEntity> publiclyVisible() {
        return (root, query, cb) -> {
            Subquery<Integer> visibleProduct = query.subquery(Integer.class);
            Root<CollectionProductEntity> link = visibleProduct.from(CollectionProductEntity.class);
            Join<CollectionProductEntity, ProductEntity> product = link.join("product");
            visibleProduct.select(cb.literal(1)).where(
                    cb.equal(link.get("collection").get("id"), root.get("id")),
                    productVisible(cb, product.get("status"))
            );
            return cb.and(
                    cb.equal(root.get("status"), RecordStatus.ACTIVE),
                    cb.exists(visibleProduct)
            );
        };
    }

    public static Specification<CollectionEntity> hasSlug(String slug) {
        return (root, query, cb) -> cb.equal(root.get("slug"), slug);
    }

    /** Lọc Product trước khi phân trang; fetch to-one chỉ ở SELECT, không fetch trong COUNT. */
    public static Specification<CollectionProductEntity> visibleProductsOf(UUID collectionId) {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("product", JoinType.INNER);
            }
            return cb.and(
                    cb.equal(root.get("collection").get("id"), collectionId),
                    productVisible(cb, root.get("product").get("status"))
            );
        };
    }

    private static Predicate productVisible(CriteriaBuilder cb, Path<RecordStatus> status) {
        return cb.or(cb.isNull(status), cb.equal(status, RecordStatus.ACTIVE));
    }
}
