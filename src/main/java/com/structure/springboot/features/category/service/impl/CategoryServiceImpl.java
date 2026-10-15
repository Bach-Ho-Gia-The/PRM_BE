package com.structure.springboot.features.category.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.request.CategoryUpdateRequest;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.util.SlugUtils;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.infrastructure.persistence.StatusSpecifications;
import com.structure.springboot.features.category.dto.response.CategoryResponse;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.mapper.CategoryMapper;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.category.service.CategoryService;
import com.structure.springboot.features.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByCategoryNameIgnoreCase(request.categoryName().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Category already exists: " + request.categoryName());
        }

        CategoryEntity entity = categoryMapper.toEntity(request);
        entity.setCategoryName(request.categoryName().trim());
        entity.setSlug(resolveSlug(request.slug(), request.categoryName(), null));

        return categoryMapper.toResponse(categoryRepository.save(entity));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryUpdateRequest request) {
        CategoryEntity entity = requireManageable(id);

        // PATCH: không gửi categoryName nghĩa là giữ nguyên tên cũ, bỏ qua luôn bước kiểm tra trùng.
        String newName = request.categoryName() == null ? null : request.categoryName().trim();
        if (newName != null && categoryRepository.existsByCategoryNameIgnoreCaseAndIdNot(newName, id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Category already exists: " + newName);
        }

        categoryMapper.updateEntity(request, entity);
        if (newName != null) {
            entity.setCategoryName(newName);
        }

        // Slug CỐ Ý không tự đổi theo tên: đường dẫn /bo-suu-tap/{slug} đã nằm trong link chia sẻ.
        if (request.slug() != null) {
            entity.setSlug(resolveSlug(request.slug(), entity.getCategoryName(), id));
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

        // Vẫn chặn khi còn sản phẩm dù đây là xoá mềm: danh mục biến mất mà sản phẩm vẫn trỏ vào nó
        // thì trang danh mục của frontend sẽ trỏ vào hư không, và admin không hiểu vì sao hàng mất.
        if (productRepository.existsByCategoryId(id)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot delete category that still contains products: " + entity.getCategoryName());
        }

        entity.setStatus(RecordStatus.DELETED);
        categoryRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .filter(CategoryServiceImpl::isVisible)
                .map(categoryMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> getAllCategories(Pageable pageable) {
        // map() của Page giữ nguyên thông tin phân trang, chỉ đổi kiểu phần tử —
        // không được .getContent().stream() vì làm vậy là mất totalElements/totalPages.
        return PageResponse.from(categoryRepository
                .findAll(StatusSpecifications.<CategoryEntity>visible(), pageable)
                .map(categoryMapper::toResponse));
    }

    /** Cho phép thao tác trên danh mục đang ẩn, chỉ chặn danh mục đã xoá (trả 404 như chưa từng có). */
    private CategoryEntity requireManageable(UUID id) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id));

        if (entity.getStatus() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id);
        }
        return entity;
    }

    /** Hàng dữ liệu cũ chưa có cột status (null) được coi là đang hoạt động. */
    private static boolean isVisible(CategoryEntity entity) {
        return entity.getStatus() == null || entity.getStatus() == RecordStatus.ACTIVE;
    }

    /** Chuẩn hoá slug và bảo đảm không trùng. {@code currentId} null nghĩa là đang tạo mới. */
    private String resolveSlug(String requestedSlug, String name, UUID currentId) {
        String slug = SlugUtils.toSlug(requestedSlug != null ? requestedSlug : name);
        if (slug == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Cannot generate slug from category name, please provide one explicitly");
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
