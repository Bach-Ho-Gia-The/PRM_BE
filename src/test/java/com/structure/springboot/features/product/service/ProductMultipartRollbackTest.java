package com.structure.springboot.features.product.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.order.repository.OrderItemRepository;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.mapper.ProductMapper;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.service.impl.ProductServiceImpl;
import com.structure.springboot.infrastructure.external.cloudinary.CloudinaryProperties;
import com.structure.springboot.infrastructure.external.cloudinary.CloudinaryQuotaService;
import com.structure.springboot.infrastructure.external.cloudinary.CloudinaryService;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductMultipartRollbackTest {
    private final Cloudinary sdk = mock(Cloudinary.class);
    private final Uploader uploader = mock(Uploader.class);
    private final CloudinaryProperties properties = new CloudinaryProperties("cloud", "key", "secret", "prm/dev",
            DataSize.ofMegabytes(5), Set.of("png", "jpg", "webp"), Duration.ofSeconds(30));
    private final ManagedImageService images = new ManagedImageService(
            new CloudinaryService(sdk, properties, mock(CloudinaryQuotaService.class)), properties);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductServiceImpl service = new ProductServiceImpl(products, categories,
            Mappers.getMapper(ProductMapper.class), mock(OrderItemRepository.class), images);
    private final UUID categoryId = UUID.randomUUID();
    private final MockMultipartFile png = new MockMultipartFile("images", "a.png", "image/png",
            new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0});
    private final List<String> uploadedIds = new ArrayList<>();
    private ProductRequest request() {
        return new ProductRequest("Dress", null, "DRESS", BigDecimal.TEN, null, null, null, null,
                null, null, false, false, categoryId, null);
    }
    @BeforeEach
    void setup() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(categories.findById(categoryId)).thenReturn(Optional.of(
                CategoryEntity.builder().id(categoryId).status(RecordStatus.ACTIVE).build()));
        when(sdk.uploader()).thenReturn(uploader);
        when(uploader.destroy(any(String.class), anyMap())).thenReturn(Map.of("result", "ok"));
    }
    @AfterEach
    void teardown() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    private Map<String, Object> uploaded(Map<?, ?> options) {
        String id = (String) options.get("public_id");
        uploadedIds.add(id);
        return Map.of("secure_url", "https://cdn/" + id, "public_id", id);
    }
    private void assertRollbackCleansUploaded() throws Exception {
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertEquals(2, uploadedIds.size());
        for (String id : uploadedIds) verify(uploader).destroy(eq(id), anyMap());
    }
    @Test
    void laterUploadFailureTriggersCleanupOfEarlierImagesOnRollback() throws Exception {
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(inv -> {
            if (uploadedIds.size() == 2) throw new IOException("upload failed");
            return uploaded(inv.getArgument(1));
        });
        assertThrows(RuntimeException.class, () -> service.createProductWithImages(request(), List.of(png, png, png), null));
        verify(products, never()).save(any());
        assertRollbackCleansUploaded();
    }
    @Test
    void databaseFailureTriggersCleanupOfAllUploadedImagesOnRollback() throws Exception {
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(inv -> uploaded(inv.getArgument(1)));
        when(products.save(any(ProductEntity.class))).thenThrow(new DataIntegrityViolationException("failed"));
        assertThrows(DataIntegrityViolationException.class,
                () -> service.createProductWithImages(request(), List.of(png, png), null));
        assertRollbackCleansUploaded();
    }
}
