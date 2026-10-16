package com.structure.springboot.features.product.specification;

import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.enums.ProductCollection;
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

class ProductSpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void omittedFiltersDoNotAddConditions() {
        Specification<ProductEntity> filters = Specification.<ProductEntity>unrestricted()
                .and(ProductSpecifications.hasCategorySlug(null))
                .and(ProductSpecifications.hasCollection(null))
                .and(ProductSpecifications.matchesKeyword("  "))
                .and(ProductSpecifications.priceAtLeast(null))
                .and(ProductSpecifications.priceAtMost(null))
                .and(ProductSpecifications.isFeatured(null));

        assertNull(filters.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectedCollectionIsKeptWhenOtherFiltersAreOmitted() {
        Root<ProductEntity> root = mock(Root.class);
        Path<ProductCollection> collectionPath = mock(Path.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate expected = mock(Predicate.class);
        when(root.<ProductCollection>get("collection")).thenReturn(collectionPath);
        when(cb.equal(collectionPath, ProductCollection.SUNSET)).thenReturn(expected);

        Specification<ProductEntity> filters = Specification.<ProductEntity>unrestricted()
                .and(ProductSpecifications.hasCategorySlug(null))
                .and(ProductSpecifications.hasCollection(ProductCollection.SUNSET))
                .and(ProductSpecifications.matchesKeyword(null))
                .and(ProductSpecifications.priceAtLeast(null))
                .and(ProductSpecifications.priceAtMost(null))
                .and(ProductSpecifications.isFeatured(null));

        assertSame(expected, filters.toPredicate(root, mock(CriteriaQuery.class), cb));
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
