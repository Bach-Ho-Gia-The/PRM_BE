package com.structure.springboot.features.category.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.response.CategoryResponse;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.mapper.CategoryMapper;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.category.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByCategoryNameIgnoreCase(request.categoryName().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Category already exists: " + request.categoryName());
        }

        CategoryEntity entity = categoryMapper.toEntity(request);
        entity.setCategoryName(request.categoryName().trim());

        return categoryMapper.toResponse(categoryRepository.save(entity));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id));

        String newName = request.categoryName().trim();
        if (categoryRepository.existsByCategoryNameIgnoreCaseAndIdNot(newName, id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Category already exists: " + newName);
        }

        categoryMapper.updateEntity(request, entity);
        entity.setCategoryName(newName);

        return categoryMapper.toResponse(categoryRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id));

        categoryRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .map(categoryMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }
}
