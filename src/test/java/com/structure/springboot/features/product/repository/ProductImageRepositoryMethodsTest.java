package com.structure.springboot.features.product.repository;

import com.structure.springboot.features.order.entity.OrderItemEntity;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.profile.entity.ProfileEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ProductImageRepositoryMethodsTest {

    @Test
    void lockAndOrderSnapshotDerivedQueriesResolveWithoutCustomQuery() {
        assertDoesNotThrow(() -> new PartTree("findWithLockById", ProductEntity.class));
        assertDoesNotThrow(() -> new PartTree("findByIdInOrderByIdAsc", ProductEntity.class));
        assertDoesNotThrow(() -> new PartTree("existsByImageUrl", OrderItemEntity.class));
        assertDoesNotThrow(() -> new PartTree("findWithLockByAccountId", ProfileEntity.class));
    }
}
