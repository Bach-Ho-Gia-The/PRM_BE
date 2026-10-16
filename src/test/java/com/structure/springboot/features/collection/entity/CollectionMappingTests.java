package com.structure.springboot.features.collection.entity;

import com.structure.springboot.infrastructure.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionMappingTests {

    @Test
    void collectionKeepsItsIdentityAndHasAnOptionalCover() throws NoSuchFieldException {
        assertTrue(SoftDeletableEntity.class.isAssignableFrom(CollectionEntity.class));

        Column name = CollectionEntity.class.getDeclaredField("collectionName").getAnnotation(Column.class);
        Column slug = CollectionEntity.class.getDeclaredField("slug").getAnnotation(Column.class);
        Column cover = CollectionEntity.class.getDeclaredField("coverImageUrl").getAnnotation(Column.class);

        assertFalse(name.nullable());
        assertFalse(slug.nullable());
        assertTrue(slug.unique());
        assertTrue(cover.nullable());
        assertEquals(500, cover.length());
    }

    @Test
    void membershipIsUniquePerCollectionAndDoesNotCascadeToProduct() throws NoSuchFieldException {
        Table table = CollectionProductEntity.class.getAnnotation(Table.class);
        assertEquals("collection_products", table.name());
        assertTrue(Arrays.stream(table.uniqueConstraints()).anyMatch(constraint ->
                Arrays.equals(constraint.columnNames(), new String[]{"collection_id", "product_id"})));
        assertTrue(Arrays.stream(table.indexes()).anyMatch(index ->
                index.columnList().replace(" ", "").equals("collection_id,sort_order,product_id")));
        assertTrue(Arrays.stream(table.indexes()).anyMatch(index ->
                index.columnList().equals("product_id")));

        ManyToOne collection = CollectionProductEntity.class.getDeclaredField("collection").getAnnotation(ManyToOne.class);
        JoinColumn collectionId = CollectionProductEntity.class.getDeclaredField("collection").getAnnotation(JoinColumn.class);
        ManyToOne product = CollectionProductEntity.class.getDeclaredField("product").getAnnotation(ManyToOne.class);
        JoinColumn productId = CollectionProductEntity.class.getDeclaredField("product").getAnnotation(JoinColumn.class);
        Column sortOrder = CollectionProductEntity.class.getDeclaredField("sortOrder").getAnnotation(Column.class);

        assertEquals(FetchType.LAZY, collection.fetch());
        assertEquals(0, collection.cascade().length);
        assertFalse(collectionId.nullable());
        assertEquals(FetchType.LAZY, product.fetch());
        assertEquals(0, product.cascade().length);
        assertFalse(productId.nullable());
        assertFalse(sortOrder.nullable());
    }
}
