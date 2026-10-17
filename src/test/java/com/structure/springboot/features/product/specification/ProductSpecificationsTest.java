package com.structure.springboot.features.product.specification;

import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void omittedFiltersDoNotAddConditions() {
        Specification<ProductEntity> filters = Specification.<ProductEntity>unrestricted()
                .and(ProductSpecifications.hasCategorySlug(null))
                .and(ProductSpecifications.inPublishedCollection(null))
                .and(ProductSpecifications.matchesKeyword("  "))
                .and(ProductSpecifications.priceAtLeast(null))
                .and(ProductSpecifications.priceAtMost(null))
                .and(ProductSpecifications.isFeatured(null));

        assertNull(filters.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void collectionSlugUsesExistsAndPublishedStatusNotAnEnumFieldOnProduct() {
        Root<ProductEntity> root = mock(Root.class);
        Root<CollectionProductEntity> link = mock(Root.class);
        Path<Object> path = mock(Path.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        Subquery<Integer> subquery = mock(Subquery.class);
        Expression<Integer> one = mock(Expression.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate expected = mock(Predicate.class);
        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(CollectionProductEntity.class)).thenReturn(link);
        when(link.get("product")).thenReturn(path);
        when(link.get("collection")).thenReturn(path);
        when(path.get("id")).thenReturn(path);
        when(path.get("slug")).thenReturn(path);
        when(path.get("status")).thenReturn(path);
        when(root.get("id")).thenReturn(path);
        when(cb.literal(1)).thenReturn(one);
        when(subquery.select(one)).thenReturn(subquery);
        when(cb.exists(subquery)).thenReturn(expected);

        assertSame(expected, ProductSpecifications.inPublishedCollection("sunset")
                .toPredicate(root, query, cb));
        verify(cb).equal(path, "sunset");
        verify(cb).equal(path, RecordStatus.ACTIVE);
        verify(cb).exists(subquery);
    }

    @Test
    @SuppressWarnings("unchecked")
    void featuredFalseIsNotTreatedAsOmitted() {
        Root<ProductEntity> root = mock(Root.class);
        Path<Boolean> featuredPath = mock(Path.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate expected = mock(Predicate.class);
        when(root.<Boolean>get("isFeatured")).thenReturn(featuredPath);
        when(cb.equal(featuredPath, false)).thenReturn(expected);

        assertSame(expected, ProductSpecifications.isFeatured(false).toPredicate(root, mock(CriteriaQuery.class), cb));
    }
}
