package com.structure.springboot.features.order.specification;

import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderSpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void omittedStatusDoesNotAddCondition() {
        Specification<OrderEntity> filter = Specification.allOf(OrderSpecifications.hasStatus(null));

        assertNull(filter.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectedStatusIsStillApplied() {
        Root<OrderEntity> root = mock(Root.class);
        Path<OrderStatus> statusPath = mock(Path.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate expected = mock(Predicate.class);
        when(root.<OrderStatus>get("status")).thenReturn(statusPath);
        when(cb.equal(statusPath, OrderStatus.PENDING)).thenReturn(expected);

        Specification<OrderEntity> filter = Specification.allOf(OrderSpecifications.hasStatus(OrderStatus.PENDING));

        assertSame(expected, filter.toPredicate(root, mock(CriteriaQuery.class), cb));
    }
}
