package com.structure.springboot.features.order.specification;

import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Bộ lọc cho màn quản trị đơn hàng.
 *
 * <p>Cùng khuôn với {@code ProductSpecifications}: tham số null thì không thêm điều kiện lọc.
 * Dùng {@code Specification.unrestricted()} vì Spring Data JPA không chấp nhận {@code and(null)}.</p>
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<OrderEntity> hasStatus(OrderStatus status) {
        return status == null
                ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
