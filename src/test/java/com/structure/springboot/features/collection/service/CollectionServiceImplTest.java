package com.structure.springboot.features.collection.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.collection.dto.request.CollectionCreateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionProductsRequest;
import com.structure.springboot.features.collection.dto.request.CollectionUpdateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.entity.CollectionEntity;
import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import com.structure.springboot.features.collection.mapper.CollectionMapper;
import com.structure.springboot.features.collection.repository.CollectionProductRepository;
import com.structure.springboot.features.collection.repository.CollectionRepository;
import com.structure.springboot.features.collection.service.impl.CollectionServiceImpl;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.infrastructure.external.cloudinary.ImageUploadResult;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceImplTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private CollectionProductRepository collectionProductRepository;
    @Mock private ProductRepository productRepository;
    @Mock private EntityManager entityManager;
    @Mock private ManagedImageService managedImageService;

    private CollectionServiceImpl service;

    @BeforeEach
    void setUp() {
        CollectionMapper mapper = Mappers.getMapper(CollectionMapper.class);
        service = new CollectionServiceImpl(collectionRepository, collectionProductRepository, productRepository,
                mapper, entityManager, managedImageService);
    }

    @Test
    void publicListUsesDatabaseSpecificationAndDoesNotReturnAdminFields() {
        CollectionEntity collection = collection(RecordStatus.ACTIVE);
        var pageable = PageRequest.of(0, 20);
        when(collectionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(collection), pageable, 1));

        var page = service.getPublicCollections(pageable);

        assertEquals(1, page.content().size());
        assertEquals(1L, page.totalElements());
        assertEquals(collection.getSlug(), page.content().get(0).slug());
        verify(collectionRepository).findAll(any(Specification.class), eq(pageable));
        verifyNoInteractions(collectionProductRepository, productRepository);
    }

    @Test
    void collectionListsRejectUnknownSortFieldBeforeDatabaseAccess() {
        var invalid = PageRequest.of(0, 20, Sort.by("products.size"));
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.getPublicCollections(invalid)).getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.getAllCollections(invalid)).getErrorCode());
        verifyNoInteractions(collectionRepository, collectionProductRepository, productRepository);
    }

    @Test
    void publicDetailKeepsPageTotalsAndAdminDefinedProductOrder() {
        CollectionEntity collection = collection(RecordStatus.ACTIVE);
        ProductEntity product = product(RecordStatus.ACTIVE);
        product.setPrice(new BigDecimal("150.00"));
        var requested = PageRequest.of(1, 1); // Service áp thứ tự chuẩn kể cả khi caller không gửi sort.
        var ordered = PageRequest.of(1, 1, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));
        when(collectionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(collection));
        when(collectionProductRepository.findAll(any(Specification.class), eq(ordered)))
                .thenReturn(new PageImpl<>(List.of(link(collection, product, 2)), ordered, 2));

        var detail = service.getPublicCollectionBySlug(collection.getSlug(), requested);

        assertEquals(collection.getSlug(), detail.slug());
        assertEquals(2L, detail.products().totalElements());
        assertEquals(1, detail.products().page());
        assertEquals(product.getId(), detail.products().content().get(0).productId());
        assertEquals(new BigDecimal("150.00"), detail.products().content().get(0).price());
        verify(collectionProductRepository).findAll(any(Specification.class), eq(ordered));
        verifyNoInteractions(productRepository);
    }

    @Test
    void publicDetailRejectsSortOutsideAdminProductOrderBeforeDatabaseAccess() {
        var invalidSorts = List.of(
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("sortOrder"))),
                PageRequest.of(0, 20, Sort.by("price")),
                PageRequest.of(0, 20, Sort.by("id"))
        );
        for (var pageable : invalidSorts) {
            ApiException error = assertThrows(ApiException.class,
                    () -> service.getPublicCollectionBySlug("summer", pageable));
            assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        }
        verifyNoInteractions(collectionRepository, collectionProductRepository, productRepository, entityManager);
    }

    @Test
    void publicDetailReturnsNotFoundWhenSlugIsNotPublishedOrHasNoVisibleProducts() {
        when(collectionRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        var error = assertThrows(ApiException.class,
                () -> service.getPublicCollectionBySlug("missing", PageRequest.of(0, 20)));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(collectionProductRepository, productRepository);
    }

    @Test
    void createStartsHiddenWithOrderedVisibleProducts() {
        ProductEntity first = product(RecordStatus.ACTIVE);
        ProductEntity legacyVisible = product(null);
        CollectionCreateRequest request = new CollectionCreateRequest(
                "  Summer  ", null, null, List.of(first.getId(), legacyVisible.getId()));
        when(productRepository.findAllById(request.productIds())).thenReturn(List.of(legacyVisible, first));
        when(collectionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CollectionEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var response = service.createCollection(request);

        assertEquals(RecordStatus.HIDDEN, response.status());
        assertEquals("Summer", response.collectionName());
        assertEquals("summer", response.slug());
        assertNull(response.coverImageUrl());
        assertEquals(2, response.visibleProductCount());
        assertEquals(List.of(first.getId(), legacyVisible.getId()),
                response.products().stream().map(item -> item.productId()).toList());
        verify(collectionRepository).saveAndFlush(argThat(entity -> entity.getStatus() == RecordStatus.HIDDEN));
        verify(collectionProductRepository).saveAll(argThat(links -> {
            var ordered = links.iterator();
            return ordered.next().getSortOrder() == 0 && ordered.next().getSortOrder() == 1;
        }));
    }

    @Test
    void createRejectsAnEmptyOrDuplicateProductList() {
        ApiException empty = assertThrows(ApiException.class, () -> service.createCollection(
                new CollectionCreateRequest("Summer", null, null, List.of())));
        assertEquals(ErrorCode.BAD_REQUEST, empty.getErrorCode());

        UUID id = UUID.randomUUID();
        ApiException duplicate = assertThrows(ApiException.class, () -> service.createCollection(
                new CollectionCreateRequest("Summer", null, null, List.of(id, id))));
        assertEquals(ErrorCode.BAD_REQUEST, duplicate.getErrorCode());

        ApiException nullId = assertThrows(ApiException.class, () -> service.createCollection(
                new CollectionCreateRequest("Summer", null, null, Arrays.asList((UUID) null))));
        assertEquals(ErrorCode.BAD_REQUEST, nullId.getErrorCode());
        verifyNoInteractions(collectionRepository, collectionProductRepository, productRepository);
    }

    @Test
    void createRejectsAnInactiveProductWithoutSavingCollection() {
        ProductEntity hidden = product(RecordStatus.HIDDEN);
        when(productRepository.findAllById(List.of(hidden.getId()))).thenReturn(List.of(hidden));

        ApiException error = assertThrows(ApiException.class, () -> service.createCollection(
                new CollectionCreateRequest("Summer", null, null, List.of(hidden.getId()))));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        verifyNoInteractions(collectionRepository, collectionProductRepository);
    }

    @Test
    void updateClearsCoverOnlyWhenHidden() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        collection.setCoverImageUrl("https://example.com/old.jpg");
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        when(collectionProductRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collection.getId()))
                .thenReturn(List.of());

        var response = service.updateCollection(collection.getId(),
                new CollectionUpdateRequest(0L, null, null, null, true));

        assertNull(collection.getCoverImageUrl());
        assertNull(response.coverImageUrl());
        assertEquals(RecordStatus.HIDDEN, response.status());
    }

    @Test
    void coverCannotBeSetAndClearedInTheSamePatch() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        collection.setCoverImageUrl("https://example.com/old.jpg");
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));

        ApiException error = assertThrows(ApiException.class, () -> service.updateCollection(collection.getId(),
                new CollectionUpdateRequest(0L, "New name", null, "https://example.com/new.jpg", true)));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals("Summer", collection.getCollectionName());
        assertEquals("https://example.com/old.jpg", collection.getCoverImageUrl());
        verifyNoInteractions(collectionProductRepository);
    }

    @Test
    void activeCollectionCannotBeEdited() {
        CollectionEntity collection = collection(RecordStatus.ACTIVE);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));

        ApiException error = assertThrows(ApiException.class, () -> service.updateCollection(collection.getId(),
                new CollectionUpdateRequest(0L, "Other name", null, null, null)));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals("Summer", collection.getCollectionName());
        verifyNoInteractions(collectionProductRepository);
    }

    @Test
    void replaceKeepsExistingInactiveLinkButOnlyAddsAnActiveProduct() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        ProductEntity inactive = product(RecordStatus.HIDDEN);
        ProductEntity removed = product(RecordStatus.ACTIVE);
        ProductEntity added = product(RecordStatus.ACTIVE);
        CollectionProductEntity oldInactiveLink = link(collection, inactive, 1);
        CollectionProductEntity removedLink = link(collection, removed, 0);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        when(collectionProductRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collection.getId()))
                .thenReturn(List.of(removedLink, oldInactiveLink));
        when(productRepository.findAllById(List.of(added.getId()))).thenReturn(List.of(added));
        doAnswer(invocation -> {
            CollectionEntity locked = invocation.getArgument(0);
            locked.setVersion(locked.getVersion() + 1);
            return null;
        }).when(entityManager).lock(collection, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

        var response = service.replaceProducts(collection.getId(),
                new CollectionProductsRequest(0L, List.of(inactive.getId(), added.getId())));

        assertEquals(2, response.products().size());
        assertEquals(1, response.visibleProductCount());
        assertEquals(inactive.getId(), response.products().get(0).productId());
        assertEquals(1L, response.version());
        assertEquals(0, oldInactiveLink.getSortOrder());
        verify(entityManager).lock(collection, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        verify(entityManager).flush();
        verify(collectionProductRepository).deleteAllInBatch(List.of(removedLink));
        verify(collectionProductRepository).saveAll(argThat(links -> {
            CollectionProductEntity newLink = links.iterator().next();
            return newLink.getProduct() == added && newLink.getSortOrder() == 1;
        }));
    }

    @Test
    void missingOrNegativeDtoVersionIsRejectedBeforeDatabaseAccess() {
        UUID id = UUID.randomUUID();
        for (Long version : Arrays.asList(null, -1L)) {
            ApiException error = assertThrows(ApiException.class, () -> service.replaceProducts(
                    id, new CollectionProductsRequest(version, List.of(UUID.randomUUID()))));
            assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        }
        verifyNoInteractions(collectionRepository, collectionProductRepository, productRepository, entityManager);
    }

    @Test
    void staleScreenCannotOverwriteAnotherAdminsMembership() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        collection.setVersion(4L); // Admin A vừa commit; màn hình của B còn version 3.
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));

        ApiException error = assertThrows(ApiException.class, () -> service.replaceProducts(
                collection.getId(), new CollectionProductsRequest(3L, List.of(UUID.randomUUID()))));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        verifyNoInteractions(collectionProductRepository, productRepository, entityManager);
    }

    @Test
    void replaceRejectsRemovingLastLink() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));

        ApiException error = assertThrows(ApiException.class, () -> service.replaceProducts(collection.getId(),
                new CollectionProductsRequest(0L, List.of())));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verifyNoInteractions(collectionProductRepository, productRepository);
    }

    @Test
    void publishRequiresAtLeastOneVisibleLinkedProduct() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        ProductEntity inactive = product(RecordStatus.DELETED);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        when(collectionProductRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collection.getId()))
                .thenReturn(List.of(link(collection, inactive, 0)));

        ApiException error = assertThrows(ApiException.class, () -> service.publishCollection(collection.getId(), new CollectionVersionRequest(0L)));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals(RecordStatus.HIDDEN, collection.getStatus());
    }

    @Test
    void publishAndHideOnlyChangeCollectionStatus() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        ProductEntity active = product(RecordStatus.ACTIVE);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        when(collectionProductRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collection.getId()))
                .thenReturn(List.of(link(collection, active, 0)));

        var published = service.publishCollection(collection.getId(), new CollectionVersionRequest(0L));
        assertEquals(RecordStatus.ACTIVE, published.status());
        assertEquals(1, published.visibleProductCount());

        var hidden = service.hideCollection(collection.getId(), new CollectionVersionRequest(0L));
        assertEquals(RecordStatus.HIDDEN, hidden.status());
        assertEquals(RecordStatus.ACTIVE, active.getStatus());
        verifyNoInteractions(productRepository);
    }

    @Test
    void deleteSoftDeletesCollectionAndUnlinksItsProductsOnly() {
        CollectionEntity collection = collection(RecordStatus.ACTIVE);
        ProductEntity active = product(RecordStatus.ACTIVE);
        CollectionProductEntity member = link(collection, active, 0);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        when(collectionProductRepository.findByCollectionId(collection.getId())).thenReturn(List.of(member));

        service.deleteCollection(collection.getId(), 0L);

        assertEquals(RecordStatus.DELETED, collection.getStatus());
        verify(collectionProductRepository).deleteAllInBatch(List.of(member));
        verifyNoInteractions(productRepository);
        assertEquals(RecordStatus.ACTIVE, active.getStatus());
    }

    @Test
    void deletedCollectionCannotBeManaged() {
        CollectionEntity collection = collection(RecordStatus.DELETED);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));

        ApiException error = assertThrows(ApiException.class, () -> service.deleteCollection(collection.getId(), 0L));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(collectionProductRepository);
    }

    /**
     * Ảnh được upload TRƯỚC khi khoá hàng, nên một yêu cầu sai version/sai trạng thái vẫn tiêu tốn
     * một lượt upload. Đổi lại, khoá bi quan và connection của pool không bị giam trong lúc chờ
     * mạng. Lỗi vẫn phải là CONFLICT, và transaction rollback sẽ kích hook dọn ảnh vừa lên.
     */
    @Test
    void uploadCoverStillRejectsWrongVersionAndActiveCollection() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        collection.setVersion(5L);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        var file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class, () -> service.uploadCover(
                collection.getId(), new CollectionVersionRequest(4L), file)).getErrorCode());
        collection.setStatus(RecordStatus.ACTIVE);
        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class, () -> service.uploadCover(
                collection.getId(), new CollectionVersionRequest(5L), file)).getErrorCode());

        // Ảnh KHÔNG được ghi vào entity khi yêu cầu bị từ chối.
        assertNull(collection.getCoverImagePublicId());
        verify(managedImageService, never()).deleteOrFail(any(), any(), any());
    }

    @Test
    void uploadCoverStagesNewUrlAndDeletesOldManagedFileBeforeCommit() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        collection.setCoverImageUrl("https://cdn/old.png");
        collection.setCoverImagePublicId("prm/dev/collections/old");
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        var file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});
        when(managedImageService.upload(file, "collections"))
                .thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/collections/new"));

        var updated = service.uploadCover(collection.getId(), new CollectionVersionRequest(0L), file);

        assertEquals("https://cdn/new.png", updated.coverImageUrl());
        verify(collectionRepository).flush();
        verify(managedImageService).deleteOrFail("prm/dev/collections/old", "collection", collection.getId());
    }

    @Test
    void deletionFailureFromCoverReplacementReturnsErrorInsteadOfSuccess() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        collection.setCoverImagePublicId("prm/dev/collections/old");
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        var file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});
        when(managedImageService.upload(file, "collections"))
                .thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/collections/new"));
        doThrow(new ApiException(ErrorCode.BAD_GATEWAY, "Deletion failed"))
                .when(managedImageService).deleteOrFail("prm/dev/collections/old", "collection", collection.getId());

        assertEquals(ErrorCode.BAD_GATEWAY, assertThrows(ApiException.class,
                () -> service.uploadCover(collection.getId(), new CollectionVersionRequest(0L), file)).getErrorCode());
        verify(collectionRepository).flush();
    }

    @Test
    void existingJsonClearCoverDeletesManagedImageAndRetainsExternalSeed() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        collection.setCoverImageUrl("https://cdn/old.png");
        collection.setCoverImagePublicId("prm/dev/collections/old");
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));

        var cleared = service.updateCollection(collection.getId(),
                new CollectionUpdateRequest(0L, null, null, null, true));
        assertNull(cleared.coverImageUrl());
        assertNull(collection.getCoverImagePublicId());
        verify(managedImageService).deleteOrFail("prm/dev/collections/old", "collection", collection.getId());
    }

    @Test
    void jsonCannotAssignAnotherManagedCoverUrl() {
        CollectionEntity collection = collection(RecordStatus.HIDDEN);
        when(collectionRepository.findWithLockById(collection.getId())).thenReturn(Optional.of(collection));
        String forged = "https://res.cloudinary.com/cloud/image/upload/prm/dev/collections/someone-else.png";
        doThrow(new ApiException(ErrorCode.BAD_REQUEST, "Managed URLs require upload"))
                .when(managedImageService).requireUnmanagedUrl(forged);

        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class, () -> service.updateCollection(
                collection.getId(), new CollectionUpdateRequest(0L, null, null, forged, null)))
                .getErrorCode());
        verify(collectionRepository, never()).flush();
    }

    private static CollectionEntity collection(RecordStatus status) {
        CollectionEntity collection = CollectionEntity.builder()
                .collectionName("Summer")
                .slug("summer")
                .status(status)
                .version(0L)
                .build();
        collection.setId(UUID.randomUUID());
        return collection;
    }

    private static ProductEntity product(RecordStatus status) {
        ProductEntity product = ProductEntity.builder()
                .name("Shirt")
                .slug("shirt")
                .status(status)
                .build();
        product.setId(UUID.randomUUID());
        return product;
    }

    private static CollectionProductEntity link(CollectionEntity collection, ProductEntity product, int order) {
        return CollectionProductEntity.builder()
                .collection(collection)
                .product(product)
                .sortOrder(order)
                .build();
    }
}
