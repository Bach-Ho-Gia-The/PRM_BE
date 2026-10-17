package com.structure.springboot.features.category.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.request.CategoryUpdateRequest;
import com.structure.springboot.features.category.dto.response.CategoryPublicResponse;
import com.structure.springboot.features.category.dto.response.CategoryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CategoryService {
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse updateCategory(UUID id, CategoryUpdateRequest request);
    void deleteCategory(UUID id);
    CategoryResponse getAdminCategoryById(UUID id);
    PageResponse<CategoryResponse> getAdminCategories(Pageable pageable);
    CategoryPublicResponse getPublicCategoryById(UUID id);
    CategoryPublicResponse getPublicCategoryBySlug(String slug);
    PageResponse<CategoryPublicResponse> getPublicCategories(Pageable pageable);
}
