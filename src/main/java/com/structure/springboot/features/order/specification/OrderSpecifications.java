package com.structure.springboot.features.order.specification;

import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Bộ lọc cho màn quản trị đơn hàng.
 *
 * <p>Cùng khuôn với {@code ProductSpecifications}: tham số null thì trả về {@code null} để
 * {@code Specification.and()} bỏ qua, nhờ vậy service chỉ việc nối chuỗi mà không phải viết if.</p>
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<OrderEntity> hasStatus(OrderStatus status) {
        return status == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
