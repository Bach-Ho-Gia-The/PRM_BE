package com.structure.springboot.features.product.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.order.repository.OrderItemRepository;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.mapper.ProductMapper;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.service.impl.ProductServiceImpl;
import com.structure.springboot.infrastructure.external.cloudinary.ImageUploadResult;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductMultipartCreateTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ManagedImageService images = mock(ManagedImageService.class);
    private final ProductServiceImpl service = new ProductServiceImpl(products, categories,
            Mappers.getMapper(ProductMapper.class), mock(OrderItemRepository.class), images);
    private final UUID categoryId = UUID.randomUUID();
    private final MockMultipartFile a = file("a.png");
    private final MockMultipartFile b = file("b.png");
    private final MockMultipartFile c = file("c.png");

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("images", name, "image/png", new byte[]{1});
    }
    private ProductRequest request(RecordStatus status, String thumbnail) {
        return new ProductRequest("Dress", null, "DRESS-01", BigDecimal.TEN,
                null, null, null, null, null, thumbnail, false, false, categoryId, status);
    }
    private void prepare() {
        when(categories.findById(categoryId)).thenReturn(Optional.of(
                CategoryEntity.builder().id(categoryId).status(RecordStatus.ACTIVE).build()));
        when(products.save(any(ProductEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }
    private void uploads() {
        when(images.upload(a, "products")).thenReturn(new ImageUploadResult("https://cdn/a", "products/a"));
        when(images.upload(b, "products")).thenReturn(new ImageUploadResult("https://cdn/b", "products/b"));
        when(images.upload(c, "products")).thenReturn(new ImageUploadResult("https://cdn/c", "products/c"));
    }

    @Test
    void multipleImagesPersistInOrderAndDefaultFirstToCover() {
        prepare(); uploads();
        var result = service.createProductWithImages(request(RecordStatus.ACTIVE, null), List.of(a, b, c), null);
        assertEquals(RecordStatus.ACTIVE, result.status());
        assertEquals("https://cdn/a", result.thumbnail());
        assertEquals(List.of("https://cdn/a", "https://cdn/b", "https://cdn/c"),
                result.gallery().stream().map(image -> image.imageUrl()).toList());
        assertEquals(List.of(0, 1, 2), result.gallery().stream().map(image -> image.sortOrder()).toList());
        var captured = org.mockito.ArgumentCaptor.forClass(ProductEntity.class);
        verify(products).save(captured.capture());
        var product = captured.getValue();
        assertEquals("products/a", product.getThumbnailPublicId());
        assertTrue(product.getImages().stream().allMatch(image -> image.getProduct() == product));
    }

    @Test
    void explicitCoverAndHiddenStatusAreHonored() {
        prepare(); uploads();
        var result = service.createProductWithImages(request(RecordStatus.HIDDEN, null), List.of(a, b, c), 1);
        assertEquals(RecordStatus.HIDDEN, result.status());
        assertEquals("https://cdn/b", result.thumbnail());
        assertEquals(3, result.gallery().size());
    }

    @Test
    void externalThumbnailRemainsUnlessAnUploadedIndexIsSelected() {
        prepare(); uploads();
        var result = service.createProductWithImages(request(null, "https://external/photo"), List.of(a, b, c), null);
        assertEquals("https://external/photo", result.thumbnail());
        assertEquals(3, result.gallery().size());
    }

    @Test
    void optionalImagesKeepExistingHiddenRule() {
        prepare();
        var result = service.createProductWithImages(request(RecordStatus.ACTIVE, null), null, null);
        assertEquals(RecordStatus.HIDDEN, result.status());
        assertTrue(result.gallery().isEmpty());
    }

    @Test
    void invalidCoverIndexDoesNotUploadOrSave() {
        for (int index : List.of(-1, 2)) {
            var e = assertThrows(ApiException.class,
                    () -> service.createProductWithImages(request(null, null), List.of(a, b), index));
            assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
        }
        verifyNoInteractions(images);
        verify(products, never()).save(any());
    }

    @Test
    void failureOnSecondUploadDoesNotSavePartialProduct() {
        when(categories.findById(categoryId)).thenReturn(Optional.of(
                CategoryEntity.builder().id(categoryId).status(RecordStatus.ACTIVE).build()));
        when(images.upload(a, "products")).thenReturn(new ImageUploadResult("https://cdn/a", "products/a"));
        when(images.upload(b, "products")).thenThrow(new ApiException(ErrorCode.BAD_GATEWAY));
        assertThrows(ApiException.class,
                () -> service.createProductWithImages(request(null, null), List.of(a, b), null));
        verify(products, never()).save(any());
        verify(images, never()).upload(c, "products");
    }
}
