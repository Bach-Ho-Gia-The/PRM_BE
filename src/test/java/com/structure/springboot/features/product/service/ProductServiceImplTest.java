package com.structure.springboot.features.product.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.mapper.ProductMapper;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.service.impl.ProductServiceImpl;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductServiceImplTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductMapper mapper = mock(ProductMapper.class);
    private final ProductServiceImpl service = new ProductServiceImpl(products, categories, mapper);

    @Test
    void createOnlyAcceptsActiveCategoryAndNameDoesNotReassignSlug() {
        CategoryEntity active = category(RecordStatus.ACTIVE);
        ProductRequest request = new ProductRequest("Summer", null, "QA-001", new BigDecimal("200"),
                null, null, null, null, null, null, false, false, active.getId());
        ProductEntity saved = ProductEntity.builder().name("Summer").sku("QA-001")
                .price(new BigDecimal("200")).build();
        when(categories.findById(active.getId())).thenReturn(Optional.of(active));
        when(mapper.toEntity(request)).thenReturn(saved);
        when(products.save(saved)).thenReturn(saved);

        service.createProduct(request);
        assertSame(active, saved.getCategory());
        assertEquals("summer", saved.getSlug());

        saved.setId(UUID.randomUUID());
        when(products.findById(saved.getId())).thenReturn(Optional.of(saved));
        ProductUpdateRequest changeName = update("New Summer", null);
        service.updateProduct(saved.getId(), changeName);
        assertEquals("summer", saved.getSlug()); // PATCH name không đổi slug.
    }

    @Test
    void createRejectsHiddenDeletedAndLegacyNullCategories() {
        for (RecordStatus status : new RecordStatus[]{RecordStatus.HIDDEN, RecordStatus.DELETED, null}) {
            CategoryEntity category = category(status);
            when(categories.findById(category.getId())).thenReturn(Optional.of(category));
            ApiException error = assertThrows(ApiException.class, () -> service.createProduct(
                    new ProductRequest("Summer", null, "QA-002", new BigDecimal("200"), null,
                            null, null, null, null, null, false, false, category.getId())));
            assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        }
        verify(products, never()).save(any());
    }

    @Test
    void changingCategoryRequiresActiveButEditingAProductAlreadyInHiddenCategoryIsAllowed() {
        CategoryEntity hidden = category(RecordStatus.HIDDEN);
        CategoryEntity otherHidden = category(RecordStatus.HIDDEN);
        CategoryEntity active = category(RecordStatus.ACTIVE);
        ProductEntity product = ProductEntity.builder().id(UUID.randomUUID()).name("Summer")
                .sku("QA-003").slug("summer").category(hidden).status(RecordStatus.ACTIVE).build();
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(products.save(product)).thenReturn(product);
        when(categories.findById(otherHidden.getId())).thenReturn(Optional.of(otherHidden));
        when(categories.findById(active.getId())).thenReturn(Optional.of(active));

        service.updateProduct(product.getId(), update("Renamed", hidden.getId()));
        assertSame(hidden, product.getCategory());
        verify(categories, never()).findById(hidden.getId());

        ApiException error = assertThrows(ApiException.class,
                () -> service.updateProduct(product.getId(), update(null, otherHidden.getId())));
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertSame(hidden, product.getCategory());

        service.updateProduct(product.getId(), update(null, active.getId()));
        assertSame(active, product.getCategory());
    }

    @Test
    void hiddenProductIsVisibleToAdminButNotToAnyPublicGet() {
        ProductEntity hidden = ProductEntity.builder().id(UUID.randomUUID()).status(RecordStatus.HIDDEN).build();
        when(products.findById(hidden.getId())).thenReturn(Optional.of(hidden));
        when(products.findBySlug("hidden")).thenReturn(Optional.of(hidden));
        ProductDetailResponse detail = mock(ProductDetailResponse.class);
        when(mapper.toDetailResponse(hidden)).thenReturn(detail);

        assertSame(detail, service.getAdminProductById(hidden.getId()));
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.getProductById(hidden.getId())).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.getProductBySlug("hidden")).getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicAndAdminListsValidateSortInServiceBeforeRepositoryAccess() {
        var invalid = PageRequest.of(0, 20, Sort.by("status"));
        ProductFilter empty = new ProductFilter(null, null, null, null, null, null);
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.getAllProducts(empty, invalid)).getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.getAdminProducts(invalid)).getErrorCode());
        verifyNoInteractions(products);

        var page = PageRequest.of(0, 20);
        when(products.findAll(any(Specification.class), eq(page))).thenReturn(new PageImpl<>(List.of()));
        assertEquals(0, service.getAllProducts(empty, page).totalElements());
        assertEquals(0, service.getAdminProducts(page).totalElements());
    }

    @Test
    void invalidLegacyCollectionParamAndBadPriceRangeAreRejected() {
        var page = PageRequest.of(0, 20);
        for (ProductFilter invalid : List.of(
                new ProductFilter(null, "SUNSET", null, null, null, null),
                new ProductFilter(null, " ", null, null, null, null),
                new ProductFilter(null, null, null, new BigDecimal("200"), new BigDecimal("100"), null),
                new ProductFilter(null, null, null, new BigDecimal("-1"), null, null)
        )) {
            assertEquals(ErrorCode.BAD_REQUEST,
                    assertThrows(ApiException.class, () -> service.getAllProducts(invalid, page)).getErrorCode());
        }
        verifyNoInteractions(products, categories);
    }

    private static ProductUpdateRequest update(String name, UUID categoryId) {
        return new ProductUpdateRequest(name, null, null, null, null, null, null, null,
                null, null, null, null, null, categoryId);
    }

    private static CategoryEntity category(RecordStatus status) {
        return CategoryEntity.builder().id(UUID.randomUUID()).categoryName("Test")
                .slug("test").status(status).build();
    }
}
