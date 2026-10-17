package com.structure.springboot.features.product.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.util.SlugUtils;
import com.structure.springboot.common.util.SortWhitelist;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductThumbnailRequest;
import com.structure.springboot.features.product.dto.request.ProductVariantRequest;
import com.structure.springboot.features.product.dto.request.ProductVariantUpdateRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.dto.response.ProductResponse;
import com.structure.springboot.features.product.dto.response.ProductVariantResponse;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.repository.OrderItemRepository;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductImageEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import com.structure.springboot.features.product.mapper.ProductMapper;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.service.ProductService;
import com.structure.springboot.features.product.specification.ProductSpecifications;
import com.structure.springboot.infrastructure.external.cloudinary.ImageUploadResult;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.infrastructure.persistence.StatusSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Set<String> PRODUCT_SORT_FIELDS = Set.of("name", "price", "createdAt", "updatedAt");
    /*
     * nullsLast: sortOrder khai nullable=false, nhưng ddl-auto=update KHÔNG siết được NOT NULL lên
     * bảng đã có dữ liệu, nên hàng cũ vẫn có thể mang null. nextSortOrder() đã phòng null từ trước;
     * comparator phải phòng theo, nếu không sửa gallery của sản phẩm cũ sẽ ném NPE.
     */
    private static final Comparator<ProductImageEntity> IMAGE_ORDER = Comparator
            .comparing(ProductImageEntity::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ProductImageEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final OrderItemRepository orderItemRepository;
    private final ManagedImageService managedImageService;

    @Override
    @Transactional
    public ProductDetailResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ApiException(ErrorCode.CONFLICT, "Product SKU already exists: " + request.sku());
        }
        if (request.status() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A new product cannot be DELETED");
        }
        managedImageService.requireExternalImageUrl(request.thumbnail());
        CategoryEntity category = requireActiveCategory(request.categoryId());

        ProductEntity entity = productMapper.toEntity(request);
        entity.setCategory(category);
        entity.setSlug(resolveSlug(request.slug(), request.name(), null));
        // Không có ảnh thì không được lên shop. Ảnh gallery chỉ upload được SAU khi đã có id sản
        // phẩm, nên nếu vẫn để ACTIVE thì mọi sản phẩm mới đều hiện ở trang công khai với ô ảnh
        // rỗng trong lúc admin đi chọn file. Cùng quy tắc với createCollection: chuẩn bị xong mới đăng.
        if (request.status() == RecordStatus.HIDDEN || !StringUtils.hasText(request.thumbnail())) {
            entity.setStatus(RecordStatus.HIDDEN);
        }
        // Có thumbnail và không yêu cầu HIDDEN: giữ mặc định ACTIVE cho client cũ.
        return productMapper.toDetailResponse(productRepository.save(entity));
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(UUID id, ProductUpdateRequest request) {
        ProductEntity entity = requireManageableForWrite(id);

        // PATCH: field null nghĩa là "không đổi" nên mọi kiểm tra đều phải bọc null trước.
        if (request.sku() != null && productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Product SKU already exists: " + request.sku());
        }
        if (request.thumbnail() != null && !Objects.equals(request.thumbnail(), entity.getThumbnail())) {
            managedImageService.requireExternalImageUrl(request.thumbnail());
            if (StringUtils.hasText(entity.getThumbnailPublicId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Use the gallery API to change a managed thumbnail");
            }
        }

        // Sửa field khác vẫn giữ Category HIDDEN cũ; chỉ khi chuyển Category mới cần ACTIVE.
        if (request.categoryId() != null && !entity.getCategory().getId().equals(request.categoryId())) {
            entity.setCategory(requireActiveCategory(request.categoryId()));
        }
        RecordStatus previousStatus = entity.getStatus();
        productMapper.updateEntity(request, entity);

        // Slug CỐ Ý không tự đổi theo tên: link cũ đã được chia sẻ ra ngoài phải còn sống.
        if (request.slug() != null) {
            entity.setSlug(resolveSlug(request.slug(), entity.getName(), id));
        }
        if (request.thumbnail() != null && request.thumbnail().isBlank()) {
            setThumbnail(entity, entity.getImages().stream().min(IMAGE_ORDER).orElse(null));
        }
        if (entity.isVisible() && !StringUtils.hasText(entity.getThumbnail())
                && (request.thumbnail() != null || previousStatus == RecordStatus.HIDDEN)) {
            throw new ApiException(ErrorCode.CONFLICT, "An ACTIVE product must have a thumbnail");
        }
        // Soft-delete, including PATCH status=DELETED, intentionally retains all gallery assets.
        return productMapper.toDetailResponse(productRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        ProductEntity entity = requireManageableForWrite(id);
        entity.setStatus(RecordStatus.DELETED);
        productRepository.save(entity); // Keep files and order-history references on soft delete.
    }

    @Override
    @Transactional
    public ProductDetailResponse addImage(UUID id, MultipartFile file, boolean asThumbnail) {
        // Upload TRƯỚC khi chạm DB: giữ khoá bi quan trong lúc chờ mạng sẽ chặn admin khác sửa
        // cùng sản phẩm, và giam connection của pool. Request hỏng thì hook rollback dọn ảnh.
        ImageUploadResult uploaded = managedImageService.upload(file, "products");
        ProductEntity product = requireManageableForWrite(id);
        int sortOrder = nextSortOrder(product);
        ProductImageEntity image = newImage(product, uploaded, sortOrder);
        product.getImages().add(image);
        if (asThumbnail || !StringUtils.hasText(product.getThumbnail())) {
            setThumbnail(product, image);
        }
        productRepository.saveAndFlush(product);
        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse replaceImage(UUID id, UUID imageId, MultipartFile file) {
        ImageUploadResult uploaded = managedImageService.upload(file, "products");
        ProductEntity product = requireManageableForWrite(id);
        return replaceImage(product, requireImage(product, imageId), uploaded);
    }

    @Override
    @Transactional
    public ProductDetailResponse uploadThumbnail(UUID id, MultipartFile file) {
        ImageUploadResult uploaded = managedImageService.upload(file, "products");
        ProductEntity product = requireManageableForWrite(id);
        // Existing gallery thumbnail: replace its row, not merely its URL. The old managed
        // file is deleted unless an order snapshot still references it.
        ProductImageEntity selected = product.getImages().stream()
                .filter(image -> isThumbnail(product, image))
                .findFirst().orElse(null);
        if (selected != null) {
            return replaceImage(product, selected, uploaded);
        }

        // An older external/standalone thumbnail is not in the gallery. Add the new managed
        // image to the gallery and drop only a previously owned standalone file, if any.
        String oldUrl = product.getThumbnail();
        String oldPublicId = product.getThumbnailPublicId();
        int sortOrder = nextSortOrder(product);
        ProductImageEntity image = newImage(product, uploaded, sortOrder);
        product.getImages().add(image);
        setThumbnail(product, image);
        productRepository.saveAndFlush(product);
        deleteIfUnused(product, oldUrl, oldPublicId);
        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse selectThumbnail(UUID id, ProductThumbnailRequest request) {
        ProductEntity product = requireManageableForWrite(id);
        setThumbnail(product, requireImage(product, request.imageId()));
        productRepository.saveAndFlush(product);
        // The previous thumbnail remains in the gallery, so no asset is deleted here.
        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse removeImage(UUID id, UUID imageId) {
        ProductEntity product = requireManageableForWrite(id);
        ProductImageEntity removed = requireImage(product, imageId);
        String oldUrl = removed.getImageUrl();
        String oldPublicId = removed.getPublicId();
        boolean wasThumbnail = isThumbnail(product, removed);
        List<ProductImageEntity> remaining = product.getImages().stream()
                .filter(image -> image != removed).sorted(IMAGE_ORDER).toList();

        // Một guard duy nhất, đặt TRƯỚC mọi thay đổi: sản phẩm đang bán phải còn thumbnail sau khi
        // gỡ ảnh. Hai nhánh là hai cách mất thumbnail — gỡ đúng ảnh đang chọn mà không còn ảnh nào
        // thay thế, hoặc sản phẩm cũ vốn đã không có thumbnail nào.
        if (product.isVisible()
                && (wasThumbnail ? remaining.isEmpty() : !StringUtils.hasText(product.getThumbnail()))) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An ACTIVE product must keep a thumbnail; hide it or pick another image first");
        }
        if (wasThumbnail) {
            // Ưu tiên ảnh kế tiếp; hết thì quay về ảnh đầu, không còn ảnh nào thì bỏ trống.
            ProductImageEntity next = remaining.stream()
                    .filter(image -> IMAGE_ORDER.compare(image, removed) > 0)
                    .findFirst().orElse(remaining.isEmpty() ? null : remaining.get(0));
            setThumbnail(product, next);
        }
        product.getImages().remove(removed); // orphanRemoval deletes only this gallery row.
        productRepository.saveAndFlush(product);
        deleteIfUnused(product, oldUrl, oldPublicId);
        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse addVariant(UUID id, ProductVariantRequest request) {
        ProductEntity product = requireManageableForWrite(id);
        if (product.getVariants().stream().anyMatch(variant -> variant.getSize() == request.size())) {
            throw new ApiException(ErrorCode.CONFLICT, "Variant already exists for size: " + request.size());
        }
        product.getVariants().add(ProductVariantEntity.builder()
                .product(product)
                .size(request.size())
                .stockQuantity(request.stockQuantity())
                .build());
        productRepository.saveAndFlush(product);
        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateVariant(UUID id, UUID variantId, ProductVariantUpdateRequest request) {
        ProductEntity product = requireManageableForWrite(id);
        ProductVariantEntity variant = requireVariant(product, variantId);
        // Cùng câu chữ với Collection để FE xử lý xung đột phiên bản bằng một nhánh duy nhất.
        if (!Objects.equals(variant.getVersion(), request.expectedVersion())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Variant has changed; refresh before retrying with the latest version");
        }
        variant.setStockQuantity(request.stockQuantity());
        productRepository.saveAndFlush(product);
        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse removeVariant(UUID id, UUID variantId) {
        ProductEntity product = requireManageableForWrite(id);
        ProductVariantEntity variant = requireVariant(product, variantId);
        if (orderItemRepository.existsByProductIdAndSizeAndOrderStatusIn(id, variant.getSize(),
                List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPING))) {
            throw new ApiException(ErrorCode.CONFLICT, "Cannot remove a size that still has open orders");
        }
        // Cùng luật với ảnh: sản phẩm đang bán không được rơi vào trạng thái khách không mua được.
        if (product.isVisible() && product.getVariants().size() == 1) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An ACTIVE product must keep at least one size; hide it first");
        }
        product.getVariants().remove(variant); // orphanRemoval xoá hẳn dòng tồn kho.
        productRepository.saveAndFlush(product);
        return productMapper.toDetailResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getAdminVariants(UUID id) {
        return requireManageable(id).getVariants().stream()
                .map(productMapper::toVariantResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getAdminProductById(UUID id) {
        return productMapper.toDetailResponse(requireManageable(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAdminProducts(Pageable pageable) {
        SortWhitelist.check(pageable.getSort(), PRODUCT_SORT_FIELDS);
        return PageResponse.from(productRepository.findAll(
                StatusSpecifications.<ProductEntity>notDeleted().and(ProductSpecifications.fetchCategory()), pageable)
                .map(productMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductById(UUID id) {
        return productRepository.findById(id)
                .filter(ProductEntity::isVisible)
                .map(productMapper::toDetailResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .filter(ProductEntity::isVisible)
                .map(productMapper::toDetailResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product not found with slug: " + slug));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllProducts(ProductFilter filter, Pageable pageable) {
        SortWhitelist.check(pageable.getSort(), PRODUCT_SORT_FIELDS);
        validatePriceRange(filter);
        String collectionSlug = normalizeCollectionSlug(filter.collection());
        Specification<ProductEntity> spec = StatusSpecifications.<ProductEntity>visible()
                .and(ProductSpecifications.fetchCategory())
                .and(ProductSpecifications.hasCategorySlug(filter.category()))
                .and(ProductSpecifications.inPublishedCollection(collectionSlug))
                .and(ProductSpecifications.matchesKeyword(filter.search()))
                .and(ProductSpecifications.priceAtLeast(filter.minPrice()))
                .and(ProductSpecifications.priceAtMost(filter.maxPrice()))
                .and(ProductSpecifications.isFeatured(filter.featured()));

        // map() của Page giữ nguyên thông tin phân trang, chỉ đổi kiểu phần tử —
        // không được .getContent().stream() vì làm vậy là mất totalElements/totalPages.
        return PageResponse.from(productRepository.findAll(spec, pageable).map(productMapper::toResponse));
    }

    /** Ảnh đã được upload bởi method công khai trước khi mở khoá hàng; ở đây chỉ còn việc ghi DB. */
    private ProductDetailResponse replaceImage(ProductEntity product, ProductImageEntity image,
                                               ImageUploadResult uploaded) {
        String oldUrl = image.getImageUrl();
        String oldPublicId = image.getPublicId();
        boolean selected = isThumbnail(product, image);
        image.setImageUrl(uploaded.url());
        image.setPublicId(uploaded.publicId());
        if (selected) {
            setThumbnail(product, image);
        }
        // Flush constraints while the DB change is uncommitted; deletion failure rolls it back.
        productRepository.saveAndFlush(product);
        deleteIfUnused(product, oldUrl, oldPublicId);
        return productMapper.toDetailResponse(product);
    }

    private void deleteIfUnused(ProductEntity product, String oldUrl, String oldPublicId) {
        if (!StringUtils.hasText(oldPublicId)
                || !StringUtils.hasText(oldUrl)
                || Objects.equals(oldPublicId, product.getThumbnailPublicId())
                || product.getImages().stream().anyMatch(image -> oldPublicId.equals(image.getPublicId()))
                || orderItemRepository.existsByImageUrl(oldUrl)) {
            return; // Legacy URL, still selected/in gallery, or frozen in an order snapshot.
        }
        managedImageService.deleteOrFail(oldPublicId, "product", product.getId());
    }

    private int nextSortOrder(ProductEntity product) {
        int last = product.getImages().stream()
                .map(ProductImageEntity::getSortOrder).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(-1);
        if (last == Integer.MAX_VALUE) {
            throw new ApiException(ErrorCode.CONFLICT, "Product gallery sort order is full");
        }
        return last + 1;
    }

    private ProductImageEntity newImage(ProductEntity product, ImageUploadResult uploaded, int sortOrder) {
        return ProductImageEntity.builder()
                .product(product)
                .imageUrl(uploaded.url())
                .publicId(uploaded.publicId())
                .sortOrder(sortOrder)
                .build();
    }

    private ProductImageEntity requireImage(ProductEntity product, UUID imageId) {
        if (imageId == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Gallery image ID is required");
        }
        return product.getImages().stream()
                .filter(image -> Objects.equals(image.getId(), imageId))
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Gallery image not found for product: " + imageId));
    }

    private ProductVariantEntity requireVariant(ProductEntity product, UUID variantId) {
        if (variantId == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Variant ID is required");
        }
        // Tìm trong danh sách của CHÍNH sản phẩm này: tra thẳng repository thì variantId của sản
        // phẩm khác cũng sửa được.
        return product.getVariants().stream()
                .filter(variant -> Objects.equals(variant.getId(), variantId))
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Variant not found for product: " + variantId));
    }

    private boolean isThumbnail(ProductEntity product, ProductImageEntity image) {
        if (StringUtils.hasText(product.getThumbnailPublicId())) {
            return product.getThumbnailPublicId().equals(image.getPublicId());
        }
        return StringUtils.hasText(product.getThumbnail())
                && product.getThumbnail().equals(image.getImageUrl());
    }

    private void setThumbnail(ProductEntity product, ProductImageEntity image) {
        product.setThumbnail(image == null ? null : image.getImageUrl());
        product.setThumbnailPublicId(image == null ? null : image.getPublicId());
    }

    private ProductEntity requireManageableForWrite(UUID id) {
        return requireExistingManageable(productRepository.findWithLockById(id), id);
    }

    private CategoryEntity requireActiveCategory(UUID id) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found with id: " + id));
        if (category.getStatus() != RecordStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Only an ACTIVE category can be selected for a product");
        }
        return category;
    }

    private void validatePriceRange(ProductFilter filter) {
        if ((filter.minPrice() != null && filter.minPrice().signum() < 0)
                || (filter.maxPrice() != null && filter.maxPrice().signum() < 0)
                || (filter.minPrice() != null && filter.maxPrice() != null
                    && filter.minPrice().compareTo(filter.maxPrice()) > 0)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid product price range");
        }
    }

    private String normalizeCollectionSlug(String slug) {
        if (slug == null) {
            return null;
        }
        String normalized = slug.trim();
        if (normalized.length() > 100 || !normalized.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Collection filter must be a published collection slug");
        }
        return normalized;
    }

    /**
     * Lấy sản phẩm cho thao tác quản trị: cho phép cả bản ghi đang HIDDEN, chỉ chặn bản ghi đã xoá.
     * Đã xoá thì trả 404 chứ không phải 410/409 — với người gọi, nó không còn tồn tại.
     */
    private ProductEntity requireManageable(UUID id) {
        return requireExistingManageable(productRepository.findById(id), id);
    }

    private ProductEntity requireExistingManageable(Optional<ProductEntity> found, UUID id) {
        ProductEntity entity = found.orElseThrow(
                () -> new ApiException(ErrorCode.NOT_FOUND, "Product not found with id: " + id));
        if (entity.getStatus() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Product not found with id: " + id);
        }
        return entity;
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
