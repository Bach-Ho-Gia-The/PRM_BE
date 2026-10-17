package com.structure.springboot.features.category.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.util.SlugUtils;
import com.structure.springboot.common.util.SortWhitelist;
import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.request.CategoryUpdateRequest;
import com.structure.springboot.features.category.dto.response.CategoryPublicResponse;
import com.structure.springboot.features.category.dto.response.CategoryResponse;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.mapper.CategoryMapper;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.category.service.CategoryService;
import com.structure.springboot.features.category.specification.CategorySpecifications;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.specification.ProductSpecifications;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.infrastructure.persistence.StatusSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final Set<String> CATEGORY_SORT_FIELDS = Set.of("categoryName", "slug", "createdAt", "updatedAt");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = normalizeName(request.categoryName());
        if (categoryRepository.existsByCategoryNameIgnoreCase(name)) {
            throw new ApiException(ErrorCode.CONFLICT, "Category already exists: " + name);
        }

        CategoryEntity entity = categoryMapper.toEntity(request);
        entity.setCategoryName(name);
        entity.setSlug(resolveSlug(request.slug(), name, null));
        // Category mới mặc định ACTIVE và được phép chưa có Product.
        return categoryMapper.toResponse(categoryRepository.save(entity));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryUpdateRequest request) {
        CategoryEntity entity = requireManageable(id);

        String newName = request.categoryName() == null ? null : normalizeName(request.categoryName());
        if (newName != null && categoryRepository.existsByCategoryNameIgnoreCaseAndIdNot(newName, id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Category already exists: " + newName);
        }
        if (request.status() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Use DELETE to remove a category");
        }

        boolean nameChanged = newName != null && !newName.equals(entity.getCategoryName());
        String requestedSlug = request.slug();
        if (requestedSlug != null && requestedSlug.isEmpty() && !nameChanged) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A new category name is required to regenerate its slug");
        }
        // PATCH đổi tên thật sự + slug thiếu/null/"" => sinh slug mới. Slug tường minh thắng;
        // đổi slug riêng cũng được. Kiểm tra trùng TRƯỚC khi ghi để tên+slug luôn atomic.
        String newSlug = nameChanged || requestedSlug != null
                ? resolveSlug(requestedSlug == null || requestedSlug.isEmpty() ? null : requestedSlug,
                        newName != null ? newName : entity.getCategoryName(), id)
                : null;

        if (newName != null) {
            entity.setCategoryName(newName);
        }
        if (newSlug != null) {
            entity.setSlug(newSlug);
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        return categoryMapper.toResponse(categoryRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        CategoryEntity entity = requireManageable(id);

        // Product ACTIVE/HIDDEN/legacy null chặn xoá; Product DELETED không chặn.
        // Lọc ở DB bằng EXISTS, không tải danh sách Product hoặc xoá cascade.
        if (productRepository.exists(StatusSpecifications.<ProductEntity>notDeleted()
                .and(ProductSpecifications.inCategory(id)))) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot delete category that still contains products: " + entity.getCategoryName());
        }

        entity.setStatus(RecordStatus.DELETED);
        categoryRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getAdminCategoryById(UUID id) {
        return categoryMapper.toResponse(requireManageable(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> getAdminCategories(Pageable pageable) {
        SortWhitelist.check(pageable.getSort(), CATEGORY_SORT_FIELDS);
        return PageResponse.from(categoryRepository
                .findAll(StatusSpecifications.<CategoryEntity>notDeleted(), pageable)
                .map(categoryMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryPublicResponse getPublicCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .filter(CategoryServiceImpl::isPublic)
                .map(categoryMapper::toPublicResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryPublicResponse getPublicCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .filter(CategoryServiceImpl::isPublic)
                .map(categoryMapper::toPublicResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with slug: " + slug));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryPublicResponse> getPublicCategories(Pageable pageable) {
        SortWhitelist.check(pageable.getSort(), CATEGORY_SORT_FIELDS);
        return PageResponse.from(categoryRepository
                .findAll(CategorySpecifications.active(), pageable)
                .map(categoryMapper::toPublicResponse));
    }

    private CategoryEntity requireManageable(UUID id) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id));
        if (entity.getStatus() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id);
        }
        return entity;
    }

    /** Khác Product legacy null: Category phải ACTIVE tường minh mới được thấy ở cửa hàng. */
    private static boolean isPublic(CategoryEntity entity) {
        return entity.getStatus() == RecordStatus.ACTIVE;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Category name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Category name must not exceed 100 characters");
        }
        return trimmed;
    }

    /** Slug trùng kể cả của Category DELETED cũng bị chặn; giữ URL cũ không alias. */
    private String resolveSlug(String requestedSlug, String name, UUID currentId) {
        String slug = SlugUtils.toSlug(requestedSlug != null ? requestedSlug : name);
        if (slug == null || slug.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot generate a valid category slug");
        }

        boolean taken = currentId == null
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, currentId);
        if (taken) {
            throw new ApiException(ErrorCode.CONFLICT, "Category slug already exists: " + slug);
        }
        return slug;
    }
}
