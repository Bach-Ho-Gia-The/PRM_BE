package com.structure.springboot.features.product.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.util.SlugUtils;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.dto.response.ProductResponse;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.mapper.ProductMapper;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.service.ProductService;
import com.structure.springboot.features.product.specification.ProductSpecifications;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.infrastructure.persistence.StatusSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductDetailResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ApiException(ErrorCode.CONFLICT, "Product SKU already exists: " + request.sku());
        }

        CategoryEntity category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + request.categoryId()));

        ProductEntity entity = productMapper.toEntity(request);
        entity.setCategory(category);
        entity.setSlug(resolveSlug(request.slug(), request.name(), null));

        return productMapper.toDetailResponse(productRepository.save(entity));
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(UUID id, ProductUpdateRequest request) {
        ProductEntity entity = requireManageable(id);

        // PATCH: field null nghĩa là "không đổi" nên mọi kiểm tra đều phải bọc null trước.
        if (request.sku() != null && productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Product SKU already exists: " + request.sku());
        }

        if (request.categoryId() != null && !entity.getCategory().getId().equals(request.categoryId())) {
            CategoryEntity newCategory = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + request.categoryId()));
            entity.setCategory(newCategory);
        }

        productMapper.updateEntity(request, entity);

        // Slug CỐ Ý không tự đổi theo tên: link cũ đã được chia sẻ ra ngoài phải còn sống.
        // Muốn đổi thì gửi slug lên tường minh.
        if (request.slug() != null) {
            entity.setSlug(resolveSlug(request.slug(), entity.getName(), id));
        }

        return productMapper.toDetailResponse(productRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        ProductEntity entity = requireManageable(id);

        entity.setStatus(RecordStatus.DELETED);
        productRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductById(UUID id) {
        return productRepository.findById(id)
                .filter(ProductServiceImpl::isVisible)
                .map(productMapper::toDetailResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .filter(ProductServiceImpl::isVisible)
                .map(productMapper::toDetailResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product not found with slug: " + slug));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllProducts(ProductFilter filter, Pageable pageable) {
        Specification<ProductEntity> spec = StatusSpecifications.<ProductEntity>visible()
                .and(ProductSpecifications.fetchCategory())
                .and(ProductSpecifications.hasCategorySlug(filter.category()))
                .and(ProductSpecifications.hasCollection(filter.collection()))
                .and(ProductSpecifications.matchesKeyword(filter.search()))
                .and(ProductSpecifications.priceAtLeast(filter.minPrice()))
                .and(ProductSpecifications.priceAtMost(filter.maxPrice()))
                .and(ProductSpecifications.isFeatured(filter.featured()));

        // map() của Page giữ nguyên thông tin phân trang, chỉ đổi kiểu phần tử —
        // không được .getContent().stream() vì làm vậy là mất totalElements/totalPages.
        return PageResponse.from(productRepository.findAll(spec, pageable).map(productMapper::toResponse));
    }

    /**
     * Lấy sản phẩm cho thao tác quản trị: cho phép cả bản ghi đang HIDDEN, chỉ chặn bản ghi đã xoá.
     * Đã xoá thì trả 404 chứ không phải 410/409 — với người gọi, nó không còn tồn tại.
     */
    private ProductEntity requireManageable(UUID id) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product not found with id: " + id));

        if (entity.getStatus() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Product not found with id: " + id);
        }
        return entity;
    }

    /** Hàng dữ liệu cũ chưa có cột status (null) được coi là đang bán, xem {@code SoftDeletableEntity}. */
    private static boolean isVisible(ProductEntity entity) {
        return entity.getStatus() == null || entity.getStatus() == RecordStatus.ACTIVE;
    }

    /**
     * Chuẩn hoá slug và bảo đảm không trùng.
     *
     * @param requestedSlug slug client gửi lên, có thể null
     * @param name          dùng để sinh slug khi client không gửi
     * @param currentId     id của chính sản phẩm đang sửa (null khi tạo mới) để không tự báo trùng với mình
     */
    private String resolveSlug(String requestedSlug, String name, UUID currentId) {
        String slug = SlugUtils.toSlug(requestedSlug != null ? requestedSlug : name);
        if (slug == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Cannot generate slug from product name, please provide one explicitly");
        }

        boolean taken = currentId == null
                ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, currentId);

        if (taken) {
            throw new ApiException(ErrorCode.CONFLICT, "Product slug already exists: " + slug);
        }
        return slug;
    }
}
