package com.structure.springboot.features.product.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.order.repository.OrderItemRepository;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductThumbnailRequest;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductImageEntity;
import com.structure.springboot.features.product.mapper.ProductMapper;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.service.impl.ProductServiceImpl;
import com.structure.springboot.infrastructure.external.cloudinary.ImageUploadResult;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    @Mock private ProductRepository products;
    @Mock private CategoryRepository categories;
    @Mock private OrderItemRepository orderItems;
    @Mock private ManagedImageService images;
    private ProductServiceImpl service;
    private final MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});

    @BeforeEach
    void setUp() {
        ProductMapper mapper = Mappers.getMapper(ProductMapper.class);
        service = new ProductServiceImpl(products, categories, mapper, orderItems, images);
    }

    @Test
    void hiddenProductCanBeCreatedWithoutPhotoAndFirstUploadBecomesThumbnail() {
        CategoryEntity category = category();
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        ProductRequest request = new ProductRequest("New shirt", null, "SKU-NEW", new BigDecimal("100"),
                null, null, null, null, null, null, null, null, category.getId(), RecordStatus.HIDDEN);
        when(products.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        var created = service.createProduct(request);
        assertEquals(RecordStatus.HIDDEN, created.status());
        assertNull(created.thumbnail());
        // Repo mock is only used for the write lock; use the saved entity from its argument.
        org.mockito.ArgumentCaptor<ProductEntity> saved = org.mockito.ArgumentCaptor.forClass(ProductEntity.class);
        verify(products).save(saved.capture());
        ProductEntity product = saved.getValue();
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));
        when(images.upload(file, "products")).thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/products/new"));

        var updated = service.addImage(product.getId(), file, false);

        assertEquals("https://cdn/new.png", updated.thumbnail());
        assertEquals(1, updated.gallery().size());
        assertEquals("https://cdn/new.png", updated.gallery().get(0).imageUrl());
        assertFalse(List.of(updated.getClass().getRecordComponents()).stream()
                .anyMatch(component -> component.getName().toLowerCase().contains("publicid")));
        assertFalse(List.of(updated.gallery().get(0).getClass().getRecordComponents()).stream()
                .anyMatch(component -> component.getName().toLowerCase().contains("publicid")));
        verifyNoInteractions(orderItems);
    }

    @Test
    void productCreationStaysHiddenWithoutThumbnailAndForbidsCreatingDeletedProduct() {
        CategoryEntity category = category();
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        when(products.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Ảnh gallery chỉ upload được SAU khi đã có id sản phẩm, nên sản phẩm tạo mà chưa có
        // thumbnail bắt buộc phải nằm HIDDEN, nếu không nó hiện ở trang công khai với ô ảnh rỗng.
        ProductRequest noThumbnail = new ProductRequest("Legacy", null, "SKU-LEGACY", BigDecimal.TEN,
                null, null, null, null, null, null, null, null, category.getId(), null);
        assertEquals(RecordStatus.HIDDEN, service.createProduct(noThumbnail).status());

        // Có sẵn ảnh ngoài thì vẫn giữ mặc định ACTIVE như client cũ vẫn trông đợi.
        ProductRequest withThumbnail = new ProductRequest("Legacy 2", null, "SKU-LEGACY-2", BigDecimal.TEN,
                null, null, null, null, null, "https://cdn/seed.png", null, null, category.getId(), null);
        assertEquals(RecordStatus.ACTIVE, service.createProduct(withThumbnail).status());

        // Xin HIDDEN thì vẫn HIDDEN dù đã có ảnh.
        ProductRequest hidden = new ProductRequest("Legacy 3", null, "SKU-LEGACY-3", BigDecimal.TEN,
                null, null, null, null, null, "https://cdn/seed.png", null, null, category.getId(),
                RecordStatus.HIDDEN);
        assertEquals(RecordStatus.HIDDEN, service.createProduct(hidden).status());
        ProductRequest deleted = new ProductRequest("Deleted", null, "SKU-DELETED", BigDecimal.TEN,
                null, null, null, null, null, null, null, null, category.getId(), RecordStatus.DELETED);
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.createProduct(deleted)).getErrorCode());
    }

    @Test
    void replacingThumbnailReferencedByOrderKeepsOldFileAndUpdatesCurrentProduct() {
        ProductEntity product = product(RecordStatus.ACTIVE);
        ProductImageEntity old = gallery(product, 0, "https://cdn/old.png", "prm/dev/products/old");
        product.setThumbnail(old.getImageUrl());
        product.setThumbnailPublicId(old.getPublicId());
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));
        when(images.upload(file, "products")).thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/products/new"));
        when(orderItems.existsByImageUrl(old.getImageUrl())).thenReturn(true);

        var updated = service.replaceImage(product.getId(), old.getId(), file);

        assertEquals(old.getId(), updated.gallery().get(0).id());
        assertEquals("https://cdn/new.png", updated.thumbnail());
        verify(orderItems).existsByImageUrl("https://cdn/old.png");
        verify(images, never()).deleteOrFail(eq("prm/dev/products/old"), any(), any());
    }

    @Test
    void removingCurrentThumbnailSelectsTheNextGalleryImageAndDeletesUnreferencedFile() {
        ProductEntity product = product(RecordStatus.ACTIVE);
        gallery(product, 0, "https://cdn/first.png", "prm/dev/products/first");
        ProductImageEntity selected = gallery(product, 1, "https://cdn/second.png", "prm/dev/products/second");
        ProductImageEntity next = gallery(product, 2, "https://cdn/third.png", "prm/dev/products/third");
        product.setThumbnail(selected.getImageUrl());
        product.setThumbnailPublicId(selected.getPublicId());
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));

        var updated = service.removeImage(product.getId(), selected.getId());

        assertEquals(next.getImageUrl(), updated.thumbnail());
        assertEquals(List.of(0, 2), updated.gallery().stream().map(item -> item.sortOrder()).toList());
        verify(images).deleteOrFail(selected.getPublicId(), "product", product.getId());
        verify(products).saveAndFlush(product);
    }

    @Test
    void cannotRemoveLastImageOfActiveProductButHiddenProductCanRemoveIt() {
        ProductEntity product = product(RecordStatus.ACTIVE);
        ProductImageEntity image = gallery(product, 0, "https://cdn/only.png", "prm/dev/products/only");
        product.setThumbnail(image.getImageUrl());
        product.setThumbnailPublicId(image.getPublicId());
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));

        ApiException error = assertThrows(ApiException.class, () -> service.removeImage(product.getId(), image.getId()));
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        verifyNoInteractions(images, orderItems);
        verify(products, never()).saveAndFlush(any());

        product.setStatus(RecordStatus.HIDDEN);
        var updated = service.removeImage(product.getId(), image.getId());
        assertNull(updated.thumbnail());
        assertTrue(updated.gallery().isEmpty());
        verify(images).deleteOrFail(image.getPublicId(), "product", product.getId());
    }

    @Test
    void replacingOldManagedPhotoFailsIfCloudinaryCannotDeleteIt() {
        ProductEntity product = product(RecordStatus.ACTIVE);
        ProductImageEntity old = gallery(product, 0, "https://cdn/old.png", "prm/dev/products/old");
        product.setThumbnail(old.getImageUrl());
        product.setThumbnailPublicId(old.getPublicId());
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));
        when(images.upload(file, "products")).thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/products/new"));
        String oldPublicId = old.getPublicId();
        doThrow(new ApiException(ErrorCode.BAD_GATEWAY, "Deletion failed"))
                .when(images).deleteOrFail(oldPublicId, "product", product.getId());

        assertEquals(ErrorCode.BAD_GATEWAY,
                assertThrows(ApiException.class, () -> service.replaceImage(product.getId(), old.getId(), file))
                        .getErrorCode());
        verify(products).saveAndFlush(product); // Uncommitted write is rolled back by @Transactional.
        verify(images).deleteOrFail(oldPublicId, "product", product.getId());
    }

    @Test
    void uploadingThumbnailAfterExternalStandaloneImageAddsNewGalleryRowWithoutDeletingExternal() {
        ProductEntity product = product(RecordStatus.HIDDEN);
        product.setThumbnail("https://external.example/old.jpg");
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));
        when(images.upload(file, "products"))
                .thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/products/new"));

        var updated = service.uploadThumbnail(product.getId(), file);

        assertEquals("https://cdn/new.png", updated.thumbnail());
        assertEquals(1, updated.gallery().size());
        verifyNoInteractions(orderItems);
        verify(images, never()).deleteOrFail(any(), any(), any());
    }

    @Test
    void removingLastSortedThumbnailWrapsToFirstGalleryImage() {
        ProductEntity product = product(RecordStatus.ACTIVE);
        ProductImageEntity first = gallery(product, 0, "https://cdn/first.png", "prm/dev/products/first");
        ProductImageEntity last = gallery(product, 1, "https://cdn/last.png", "prm/dev/products/last");
        product.setThumbnail(last.getImageUrl());
        product.setThumbnailPublicId(last.getPublicId());
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));

        assertEquals(first.getImageUrl(), service.removeImage(product.getId(), last.getId()).thumbnail());
        verify(images).deleteOrFail("prm/dev/products/last", "product", product.getId());
    }

    @Test
    void selectingExistingGalleryPhotoDoesNotDeleteOldOneAndSoftDeleteKeepsAllAssets() {
        ProductEntity product = product(RecordStatus.ACTIVE);
        ProductImageEntity first = gallery(product, 0, "https://cdn/first.png", "prm/dev/products/first");
        ProductImageEntity second = gallery(product, 1, "https://cdn/second.png", "prm/dev/products/second");
        product.setThumbnail(first.getImageUrl());
        product.setThumbnailPublicId(first.getPublicId());
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));

        var response = service.selectThumbnail(product.getId(), new ProductThumbnailRequest(second.getId()));
        assertEquals(second.getImageUrl(), response.thumbnail());
        service.deleteProduct(product.getId());
        assertEquals(RecordStatus.DELETED, product.getStatus());
        verifyNoInteractions(images, orderItems);
    }

    @Test
    void orderCheckoutMustLockProductRowsWhileCapturingImageUrls() throws NoSuchMethodException {
        assertEquals(LockModeType.PESSIMISTIC_READ,
                ProductRepository.class.getMethod("findByIdInOrderByIdAsc", java.util.Collection.class)
                        .getAnnotation(Lock.class).value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE,
                ProductRepository.class.getMethod("findWithLockById", UUID.class)
                        .getAnnotation(Lock.class).value());
    }

    private static ProductEntity product(RecordStatus status) {
        ProductEntity product = ProductEntity.builder().id(UUID.randomUUID()).name("Shirt")
                .slug("shirt").sku("SKU-1").price(BigDecimal.TEN).status(status)
                .category(category()).build();
        return product;
    }

    private static CategoryEntity category() {
        return CategoryEntity.builder().id(UUID.randomUUID()).categoryName("Shirts")
                .slug("shirts").status(RecordStatus.ACTIVE).build();
    }

    private static ProductImageEntity gallery(ProductEntity product, int order, String url, String publicId) {
        ProductImageEntity image = ProductImageEntity.builder().id(UUID.randomUUID()).product(product)
                .imageUrl(url).publicId(publicId).sortOrder(order).build();
        product.getImages().add(image);
        return image;
    }
}
