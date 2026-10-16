package com.structure.springboot.features.category.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.request.CategoryUpdateRequest;
import com.structure.springboot.features.category.dto.response.CategoryPublicResponse;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.mapper.CategoryMapper;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.category.service.impl.CategoryServiceImpl;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CategoryServiceImplTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryMapper mapper = mock(CategoryMapper.class);
    private final CategoryServiceImpl service = new CategoryServiceImpl(categories, products, mapper);

    @Test
    void createAllowsEmptyCategoryAndGeneratesSlugWhenAbsent() {
        CategoryRequest request = new CategoryRequest("  Váy Chữ A  ", null);
        CategoryEntity entity = CategoryEntity.builder().build();
        when(mapper.toEntity(request)).thenReturn(entity);
        when(categories.save(entity)).thenReturn(entity);

        service.createCategory(request);
        assertEquals("Váy Chữ A", entity.getCategoryName());
        assertEquals("vay-chu-a", entity.getSlug());
        assertEquals(RecordStatus.ACTIVE, entity.getStatus());
        assertEquals(0, entity.getProducts().size());
    }

    @Test
    void renameWithoutSlugOrWithEmptySlugRegeneratesButExplicitSlugWins() {
        CategoryEntity entity = category(RecordStatus.ACTIVE);
        when(categories.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(categories.save(entity)).thenReturn(entity);

        service.updateCategory(entity.getId(), new CategoryUpdateRequest("Váy Mùa Hè", null, null));
        assertEquals("vay-mua-he", entity.getSlug());

        service.updateCategory(entity.getId(), new CategoryUpdateRequest("Váy Mùa Thu", "", null));
        assertEquals("vay-mua-thu", entity.getSlug());

        service.updateCategory(entity.getId(), new CategoryUpdateRequest("Váy Mới", "slug-tuy-chon", null));
        assertEquals("slug-tuy-chon", entity.getSlug());

        service.updateCategory(entity.getId(), new CategoryUpdateRequest(null, "slug-moi", null));
        assertEquals("Váy Mới", entity.getCategoryName());
        assertEquals("slug-moi", entity.getSlug());

        service.updateCategory(entity.getId(), new CategoryUpdateRequest("Váy Mới", null, null));
        assertEquals("slug-moi", entity.getSlug()); // Không đổi tên thật sự: giữ slug.
    }

    @Test
    void duplicateGeneratedSlugAndDuplicateNameAreAtomicConflicts() {
        CategoryEntity entity = category(RecordStatus.HIDDEN);
        when(categories.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(categories.existsBySlugAndIdNot("trung-slug", entity.getId())).thenReturn(true);
        when(categories.existsByCategoryNameIgnoreCaseAndIdNot("Trùng Tên", entity.getId())).thenReturn(true);

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class, () -> service.updateCategory(
                entity.getId(), new CategoryUpdateRequest("Trùng Slug", null, RecordStatus.ACTIVE))).getErrorCode());
        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class, () -> service.updateCategory(
                entity.getId(), new CategoryUpdateRequest("Trùng Tên", "khac", null))).getErrorCode());
        assertEquals("Tên cũ", entity.getCategoryName());
        assertEquals("slug-cu", entity.getSlug());
        assertEquals(RecordStatus.HIDDEN, entity.getStatus());
        verify(categories, never()).save(any());
    }

    @Test
    void rejectDeletedViaPatchAndInvalidNamesOrEmptySlugWithoutRename() {
        CategoryEntity entity = category(RecordStatus.ACTIVE);
        when(categories.findById(entity.getId())).thenReturn(Optional.of(entity));
        for (CategoryUpdateRequest request : List.of(
                new CategoryUpdateRequest(null, null, RecordStatus.DELETED),
                new CategoryUpdateRequest("   ", null, null),
                new CategoryUpdateRequest(null, "", null),
                new CategoryUpdateRequest("Tên cũ", "", null))) {
            assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                    () -> service.updateCategory(entity.getId(), request)).getErrorCode());
        }
        assertEquals("Tên cũ", entity.getCategoryName());
        verify(categories, never()).save(any());
    }

    @Test
    void publicDetailRequiresActiveButAdminCanInspectHiddenAndLegacyNull() {
        for (RecordStatus status : new RecordStatus[]{RecordStatus.ACTIVE, RecordStatus.HIDDEN,
                RecordStatus.DELETED, null}) {
            CategoryEntity entity = category(status);
            when(categories.findById(entity.getId())).thenReturn(Optional.of(entity));
            when(categories.findBySlug(entity.getSlug())).thenReturn(Optional.of(entity));
            CategoryPublicResponse publicCategory = new CategoryPublicResponse(
                    entity.getId(), entity.getCategoryName(), entity.getSlug());
            when(mapper.toPublicResponse(entity)).thenReturn(publicCategory);

            if (status == RecordStatus.ACTIVE) {
                assertSame(publicCategory, service.getPublicCategoryById(entity.getId()));
                assertSame(publicCategory, service.getPublicCategoryBySlug(entity.getSlug()));
            } else {
                assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                        () -> service.getPublicCategoryById(entity.getId())).getErrorCode());
                assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                        () -> service.getPublicCategoryBySlug(entity.getSlug())).getErrorCode());
            }
            if (status == RecordStatus.DELETED) {
                assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                        () -> service.getAdminCategoryById(entity.getId())).getErrorCode());
            } else {
                service.getAdminCategoryById(entity.getId());
                verify(mapper).toResponse(entity);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void sortValidationIsInServiceAndListPagesKeepMetadata() {
        var invalid = PageRequest.of(0, 20, Sort.by("name"));
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.getAdminCategories(invalid)).getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.getPublicCategories(invalid)).getErrorCode());
        verifyNoInteractions(categories);

        var page = PageRequest.of(0, 1, Sort.by("categoryName"));
        when(categories.findAll(any(Specification.class), eq(page)))
                .thenReturn(new PageImpl<>(List.of(category(RecordStatus.ACTIVE)), page, 3));
        assertEquals(3, service.getAdminCategories(page).totalElements());
        assertEquals(3, service.getPublicCategories(page).totalElements());
        assertFalse(service.getPublicCategories(page).last());
    }

    @Test
    void deleteUsesNotDeletedProductExistenceAndNeverCascades() {
        CategoryEntity entity = category(RecordStatus.HIDDEN);
        when(categories.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(products.exists(any(Specification.class))).thenReturn(true, false);

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> service.deleteCategory(entity.getId())).getErrorCode());
        assertEquals(RecordStatus.HIDDEN, entity.getStatus());
        service.deleteCategory(entity.getId());
        assertEquals(RecordStatus.DELETED, entity.getStatus());
        verify(categories).save(entity);
    }

    private static CategoryEntity category(RecordStatus status) {
        return CategoryEntity.builder().id(UUID.randomUUID()).categoryName("Tên cũ")
                .slug("slug-cu").status(status).build();
    }
}
