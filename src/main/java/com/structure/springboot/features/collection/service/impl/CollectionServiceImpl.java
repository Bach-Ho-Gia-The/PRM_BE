package com.structure.springboot.features.collection.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.util.SlugUtils;
import com.structure.springboot.common.util.SortWhitelist;
import com.structure.springboot.features.collection.dto.request.CollectionCreateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionProductsRequest;
import com.structure.springboot.features.collection.dto.request.CollectionUpdateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.dto.response.CollectionDetailResponse;
import com.structure.springboot.features.collection.dto.response.CollectionPublicDetailResponse;
import com.structure.springboot.features.collection.dto.response.CollectionPublicSummaryResponse;
import com.structure.springboot.features.collection.dto.response.CollectionSummaryResponse;
import com.structure.springboot.features.collection.entity.CollectionEntity;
import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import com.structure.springboot.features.collection.mapper.CollectionMapper;
import com.structure.springboot.features.collection.repository.CollectionProductRepository;
import com.structure.springboot.features.collection.repository.CollectionRepository;
import com.structure.springboot.features.collection.service.CollectionService;
import com.structure.springboot.features.collection.specification.CollectionSpecifications;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.infrastructure.external.cloudinary.ImageUploadResult;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.infrastructure.persistence.StatusSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {

    private static final Set<String> COLLECTION_SORT_FIELDS = Set.of("collectionName", "slug", "createdAt", "updatedAt");

    private final CollectionRepository collectionRepository;
    private final CollectionProductRepository collectionProductRepository;
    private final ProductRepository productRepository;
    private final CollectionMapper collectionMapper;
    private final EntityManager entityManager;
    private final ManagedImageService managedImageService;

    @Override
    @Transactional
    public CollectionDetailResponse createCollection(CollectionCreateRequest request) {
        String name = normalizeName(request.collectionName());
        List<UUID> ids = requireOrderedProductIds(request.productIds());
        Map<UUID, ProductEntity> products = requireVisibleProducts(ids);

        CollectionEntity collection = collectionMapper.toEntity(request);
        collection.setCollectionName(name);
        collection.setSlug(resolveSlug(request.slug(), name, null));
        collection.setCoverImageUrl(normalizeCoverUrl(request.coverImageUrl()));
        managedImageService.requireUnmanagedUrl(collection.getCoverImageUrl());
        // SoftDeletableEntity mặc định ACTIVE; BST phải được chuẩn bị rồi mới đăng.
        collection.setStatus(RecordStatus.HIDDEN);
        collectionRepository.saveAndFlush(collection);

        List<CollectionProductEntity> links = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            links.add(CollectionProductEntity.builder()
                    .collection(collection)
                    .product(products.get(ids.get(i)))
                    .sortOrder(i)
                    .build());
        }
        collectionProductRepository.saveAll(links);
        return toDetailResponse(collection, links);
    }

    @Override
    @Transactional
    public CollectionDetailResponse updateCollection(UUID id, CollectionUpdateRequest request) {
        CollectionEntity collection = requireHidden(id, request.expectedVersion());
        if (Boolean.TRUE.equals(request.clearCoverImage()) && request.coverImageUrl() != null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot set and clear collection cover in one request");
        }
        String requestedCover = request.coverImageUrl() == null
                ? null : normalizeCoverUrl(request.coverImageUrl());
        if (requestedCover != null && !requestedCover.equals(collection.getCoverImageUrl())) {
            managedImageService.requireUnmanagedUrl(requestedCover);
        }
        boolean coverChanged = (Boolean.TRUE.equals(request.clearCoverImage())
                && (collection.getCoverImageUrl() != null || collection.getCoverImagePublicId() != null))
                || (requestedCover != null && !requestedCover.equals(collection.getCoverImageUrl()));
        String oldPublicId = collection.getCoverImagePublicId();

        if (request.collectionName() != null) {
            collection.setCollectionName(normalizeName(request.collectionName()));
        }
        // Không tự thay slug khi đổi tên: link cũ đã được chia sẻ vẫn phải hoạt động.
        if (request.slug() != null) {
            collection.setSlug(resolveSlug(request.slug(), collection.getCollectionName(), id));
        }
        if (coverChanged) {
            collection.setCoverImageUrl(Boolean.TRUE.equals(request.clearCoverImage()) ? null : requestedCover);
            collection.setCoverImagePublicId(null);
        }

        collectionRepository.flush(); // Check DB constraints and @Version before deleting an old asset.
        if (coverChanged) {
            managedImageService.deleteOrFail(oldPublicId, "collection", id);
        }
        return toDetailResponse(collection);
    }

    @Override
    @Transactional
    public CollectionDetailResponse uploadCover(UUID id, CollectionVersionRequest request, MultipartFile file) {
        // Upload TRƯỚC khi khoá hàng: chờ mạng trong lúc đang giữ khoá sẽ chặn admin khác và
        // giam connection của pool. Request hỏng thì hook rollback dọn ảnh vừa lên.
        ImageUploadResult uploaded = managedImageService.upload(file, "collections");
        CollectionEntity collection = requireHidden(id, request.expectedVersion());
        String oldPublicId = collection.getCoverImagePublicId();
        collection.setCoverImageUrl(uploaded.url());
        collection.setCoverImagePublicId(uploaded.publicId());
        collectionRepository.flush(); // Uncommitted until Cloudinary confirms deletion of the old cover.
        managedImageService.deleteOrFail(oldPublicId, "collection", id);
        return toDetailResponse(collection);
    }

    @Override
    @Transactional
    public CollectionDetailResponse replaceProducts(UUID id, CollectionProductsRequest request) {
        CollectionEntity collection = requireHidden(id, request.expectedVersion());
        List<UUID> ids = requireOrderedProductIds(request.productIds());
        List<CollectionProductEntity> current = collectionProductRepository
                .findByCollectionIdOrderBySortOrderAscIdAsc(id);
        Map<UUID, CollectionProductEntity> existing = current.stream()
                .collect(Collectors.toMap(link -> link.getProduct().getId(), Function.identity()));
        Set<UUID> requestedIds = new HashSet<>(ids);

        // Sản phẩm đã liên kết từ trước có thể đã bị ẩn/xoá mềm: cho giữ và sắp lại,
        // nhưng chỉ được thêm ID mới nếu Product đang hiển thị.
        List<UUID> newIds = ids.stream().filter(productId -> !existing.containsKey(productId)).toList();
        Map<UUID, ProductEntity> newProducts = requireVisibleProducts(newIds);

        List<CollectionProductEntity> removed = current.stream()
                .filter(link -> !requestedIds.contains(link.getProduct().getId()))
                .toList();
        if (!removed.isEmpty()) {
            collectionProductRepository.deleteAllInBatch(removed);
        }

        List<CollectionProductEntity> links = new ArrayList<>(ids.size());
        List<CollectionProductEntity> added = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            UUID productId = ids.get(i);
            CollectionProductEntity link = existing.get(productId);
            if (link == null) {
                link = CollectionProductEntity.builder()
                        .collection(collection)
                        .product(newProducts.get(productId))
                        .build();
                added.add(link);
            }
            link.setSortOrder(i);
            links.add(link);
        }
        if (!added.isEmpty()) {
            collectionProductRepository.saveAll(added);
        }
        // Thay đổi bảng liên kết không tự làm @Version của Collection tăng.
        // Entity đã giữ PESSIMISTIC_WRITE: dùng PESSIMISTIC_FORCE_INCREMENT (không phải
        // OPTIMISTIC_FORCE_INCREMENT, vì Hibernate có thể bỏ qua lock mode yếu hơn).
        entityManager.lock(collection, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        return toDetailResponse(collection, links);
    }

    @Override
    @Transactional
    public CollectionDetailResponse publishCollection(UUID id, CollectionVersionRequest request) {
        CollectionEntity collection = requireHidden(id, request.expectedVersion());
        List<CollectionProductEntity> links = collectionProductRepository
                .findByCollectionIdOrderBySortOrderAscIdAsc(id);
        if (links.stream().noneMatch(link -> link.getProduct().isVisible())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Collection must contain at least one visible product before publishing");
        }
        collection.setStatus(RecordStatus.ACTIVE);
        collectionRepository.flush();
        return toDetailResponse(collection, links);
    }

    @Override
    @Transactional
    public CollectionDetailResponse hideCollection(UUID id, CollectionVersionRequest request) {
        CollectionEntity collection = requireManageableForWrite(id, request.expectedVersion());
        if (collection.getStatus() != RecordStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Only an ACTIVE collection can be hidden");
        }
        collection.setStatus(RecordStatus.HIDDEN);
        collectionRepository.flush();
        return toDetailResponse(collection);
    }

    @Override
    @Transactional
    public void deleteCollection(UUID id, long expectedVersion) {
        CollectionEntity collection = requireManageableForWrite(id, expectedVersion);
        // BST giữ ID/slug cho báo cáo sau này; chỉ các liên kết của BST này bị xoá thật.
        collection.setStatus(RecordStatus.DELETED);
        List<CollectionProductEntity> links = collectionProductRepository.findByCollectionId(id);
        if (!links.isEmpty()) {
            collectionProductRepository.deleteAllInBatch(links);
        }
        collectionRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionDetailResponse getCollectionById(UUID id) {
        return toDetailResponse(requireManageable(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CollectionSummaryResponse> getAllCollections(Pageable pageable) {
        SortWhitelist.check(pageable.getSort(), COLLECTION_SORT_FIELDS);
        return PageResponse.from(collectionRepository
                .findAll(StatusSpecifications.<CollectionEntity>notDeleted(), pageable)
                .map(collectionMapper::toSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CollectionPublicSummaryResponse> getPublicCollections(Pageable pageable) {
        SortWhitelist.check(pageable.getSort(), COLLECTION_SORT_FIELDS);
        return PageResponse.from(collectionRepository
                .findAll(CollectionSpecifications.publiclyVisible(), pageable)
                .map(collectionMapper::toPublicSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionPublicDetailResponse getPublicCollectionBySlug(String slug, Pageable pageable) {
        // Trang Product có một thứ tự nghiệp vụ duy nhất, không phụ thuộc sort do client tự chọn.
        SortWhitelist.check(pageable.getSort(), Set.of("sortOrder", "id"));
        if (pageable.getSort().isSorted()
                && (!pageable.getSort().iterator().next().getProperty().equals("sortOrder")
                    || pageable.getSort().stream().anyMatch(order -> order.getDirection() != Sort.Direction.ASC))) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Collection products must follow the admin-defined order");
        }
        // Kiểm tra cả status và tồn tại Product hiển thị, không dùng số phần tử của trang yêu cầu:
        // page vượt phạm vi vẫn trả chi tiết BST + trang rỗng, BST thực sự rỗng mới trả 404.
        CollectionEntity collection = collectionRepository.findOne(
                        CollectionSpecifications.publiclyVisible().and(CollectionSpecifications.hasSlug(slug)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Collection not found with slug: " + slug));
        Pageable orderedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));
        var products = PageResponse.from(collectionProductRepository
                .findAll(CollectionSpecifications.visibleProductsOf(collection.getId()), orderedPage)
                .map(collectionMapper::toPublicProductResponse));
        return new CollectionPublicDetailResponse(
                collection.getId(), collection.getCollectionName(), collection.getSlug(),
                collection.getCoverImageUrl(), products);
    }

    private CollectionEntity requireManageable(UUID id) {
        return requireNonDeleted(collectionRepository.findById(id), id);
    }

    private CollectionEntity requireManageableForWrite(UUID id, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Expected collection version must be zero or greater");
        }
        // Khoá hàng TRƯỚC khi so version: B đang chờ A phải đọc được phiên bản A vừa commit.
        CollectionEntity collection = requireNonDeleted(collectionRepository.findWithLockById(id), id);
        if (!Objects.equals(collection.getVersion(), expectedVersion)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Collection has changed; refresh before retrying with the latest version");
        }
        return collection;
    }

    private CollectionEntity requireNonDeleted(Optional<CollectionEntity> found, UUID id) {
        CollectionEntity collection = found.orElseThrow(
                () -> new ApiException(ErrorCode.NOT_FOUND, "Collection not found with id: " + id));
        if (collection.getStatus() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Collection not found with id: " + id);
        }
        return collection;
    }

    private CollectionEntity requireHidden(UUID id, Long expectedVersion) {
        CollectionEntity collection = requireManageableForWrite(id, expectedVersion);
        if (collection.getStatus() != RecordStatus.HIDDEN) {
            throw new ApiException(ErrorCode.CONFLICT, "Hide collection before editing or publishing");
        }
        return collection;
    }

    private List<UUID> requireOrderedProductIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Collection requires at least one valid product ID");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Product IDs must be unique within a collection");
        }
        return ids;
    }

    private Map<UUID, ProductEntity> requireVisibleProducts(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProductEntity> products = new HashMap<>();
        productRepository.findAllById(ids).forEach(product -> products.put(product.getId(), product));
        for (UUID id : ids) {
            ProductEntity product = products.get(id);
            if (product == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Product not found with id: " + id);
            }
            if (!product.isVisible()) {
                throw new ApiException(ErrorCode.CONFLICT, "Product is not visible: " + id);
            }
        }
        return products;
    }

    private CollectionDetailResponse toDetailResponse(CollectionEntity collection) {
        return toDetailResponse(collection,
                collectionProductRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collection.getId()));
    }

    private CollectionDetailResponse toDetailResponse(CollectionEntity collection, List<CollectionProductEntity> links) {
        return new CollectionDetailResponse(
                collection.getId(),
                collection.getCollectionName(),
                collection.getSlug(),
                collection.getCoverImageUrl(),
                collection.getStatus(),
                collection.getVersion(),
                links.stream().map(collectionMapper::toProductResponse).toList(),
                links.stream().filter(link -> link.getProduct().isVisible()).count(),
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Collection name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Collection name must not exceed 100 characters");
        }
        return trimmed;
    }

    private String resolveSlug(String requestedSlug, String name, UUID currentId) {
        String slug = SlugUtils.toSlug(requestedSlug != null ? requestedSlug : name);
        if (slug == null || slug.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot generate a valid collection slug");
        }
        boolean taken = currentId == null
                ? collectionRepository.existsBySlug(slug)
                : collectionRepository.existsBySlugAndIdNot(slug, currentId);
        if (taken) {
            throw new ApiException(ErrorCode.CONFLICT, "Collection slug already exists: " + slug);
        }
        return slug;
    }

    private String normalizeCoverUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 500) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cover URL must not exceed 500 characters");
        }
        // Ảnh bìa KHÔNG nhận chuỗi rỗng: muốn gỡ ảnh thì gửi cờ clearCoverImage.
        if (trimmed.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cover URL must use http or https");
        }
        managedImageService.requireExternalImageUrl(trimmed);
        return trimmed;
    }
}
