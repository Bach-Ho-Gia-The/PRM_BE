package com.structure.springboot.features.collection.repository;

import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Bắt sớm lỗi tên derived query kể cả trên máy không có Docker để nạp Spring context. */
class CollectionProductRepositoryMethodsTest {

    @Test
    void orderedMembershipQueryResolvesNestedCollectionIdAndSortOrder() {
        assertDoesNotThrow(() -> new PartTree(
                "findByCollectionIdOrderBySortOrderAscIdAsc", CollectionProductEntity.class));
        assertDoesNotThrow(() -> new PartTree("findByCollectionId", CollectionProductEntity.class));
    }
}
