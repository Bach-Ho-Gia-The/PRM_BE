package com.structure.springboot.features.store.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.store.dto.request.StoreRequest;
import com.structure.springboot.features.store.dto.request.StoreUpdateRequest;
import com.structure.springboot.features.store.entity.StoreEntity;
import com.structure.springboot.features.store.mapper.StoreMapper;
import com.structure.springboot.features.store.repository.StoreRepository;
import com.structure.springboot.features.store.service.impl.StoreServiceImpl;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nhãn test để ASCII thuần vì output surefire mã hoá hỏng tiếng Việt có dấu. */
@ExtendWith(MockitoExtension.class)
class StoreServiceImplTest {

    @Mock private StoreRepository stores;
    @Mock private ManagedImageService images;
    private StoreServiceImpl service;

    @BeforeEach
    void setUp() {
        StoreMapper mapper = Mappers.getMapper(StoreMapper.class);
        service = new StoreServiceImpl(stores, mapper, images);
    }

    private StoreRequest request(String name) {
        return new StoreRequest(name, "12 Nguyen Hue, Q1", "0901234567",
                "08:00 - 22:00", 10.776, 106.701, null);
    }

    private StoreEntity existing(RecordStatus status) {
        StoreEntity entity = StoreEntity.builder()
                .storeName("Chi nhanh Q1").address("12 Nguyen Hue").phone("0901234567")
                .openingHours("08:00 - 22:00").latitude(10.776).longitude(106.701)
                .status(status).build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    @Test
    void createTrimsNameAndPersists() {
        when(stores.existsByStoreNameIgnoreCase("Chi nhanh Q1")).thenReturn(false);
        when(stores.save(any(StoreEntity.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.createStore(request("  Chi nhanh Q1  "));

        assertEquals("Chi nhanh Q1", result.storeName());
        assertEquals(10.776, result.latitude());
        verify(images).requireExternalImageUrl(null);
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        when(stores.existsByStoreNameIgnoreCase("CHI NHANH Q1")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createStore(request("CHI NHANH Q1")));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(stores, never()).save(any());
    }

    @Test
    void updateKeepsUntouchedFields() {
        StoreEntity entity = existing(RecordStatus.ACTIVE);
        when(stores.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(stores.save(any(StoreEntity.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.updateStore(entity.getId(),
                new StoreUpdateRequest(null, null, null, "09:00 - 21:00", null, null, null, null));

        assertEquals("09:00 - 21:00", result.openingHours());
        assertEquals("Chi nhanh Q1", result.storeName());   // khong bi xoa
        assertEquals(10.776, result.latitude());            // toa do giu nguyen
    }

    @Test
    void updateRejectsDeletedStatusAndPointsToDeleteEndpoint() {
        StoreEntity entity = existing(RecordStatus.ACTIVE);
        when(stores.findById(entity.getId())).thenReturn(Optional.of(entity));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateStore(entity.getId(), new StoreUpdateRequest(
                        null, null, null, null, null, null, null, RecordStatus.DELETED)));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(stores, never()).save(any());
    }

    @Test
    void updateCanHideStore() {
        StoreEntity entity = existing(RecordStatus.ACTIVE);
        when(stores.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(stores.save(any(StoreEntity.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.updateStore(entity.getId(), new StoreUpdateRequest(
                null, null, null, null, null, null, null, RecordStatus.HIDDEN));

        assertEquals(RecordStatus.HIDDEN, result.status());
    }

    @Test
    void updateValidatesImageUrlScheme() {
        StoreEntity entity = existing(RecordStatus.ACTIVE);
        when(stores.findById(entity.getId())).thenReturn(Optional.of(entity));

        service.updateStore(entity.getId(), new StoreUpdateRequest(
                null, null, null, null, null, null, "https://cdn.test/a.png", null));

        verify(images).requireExternalImageUrl("https://cdn.test/a.png");
    }

    @Test
    void deleteIsSoftAndSecondCallReturnsNotFound() {
        StoreEntity entity = existing(RecordStatus.ACTIVE);
        when(stores.findById(entity.getId())).thenReturn(Optional.of(entity));

        service.deleteStore(entity.getId());
        assertEquals(RecordStatus.DELETED, entity.getStatus());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.deleteStore(entity.getId()));
        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void publicDetailHidesHiddenStoreButAdminDetailShowsIt() {
        StoreEntity entity = existing(RecordStatus.HIDDEN);
        when(stores.findById(entity.getId())).thenReturn(Optional.of(entity));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.getPublicStoreById(entity.getId()));
        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());

        assertEquals("Chi nhanh Q1", service.getAdminStoreById(entity.getId()).storeName());
    }

    @Test
    void publicDetailTreatsLegacyNullStatusAsVisible() {
        StoreEntity entity = existing(null);
        when(stores.findById(entity.getId())).thenReturn(Optional.of(entity));

        assertEquals("Chi nhanh Q1", service.getPublicStoreById(entity.getId()).storeName());
    }

    @Test
    void missingStoreReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(stores.findById(id)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.getAdminStoreById(id)).getErrorCode());
    }
}
