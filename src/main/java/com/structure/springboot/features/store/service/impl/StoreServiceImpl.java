package com.structure.springboot.features.store.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.util.SortWhitelist;
import com.structure.springboot.features.store.dto.request.StoreRequest;
import com.structure.springboot.features.store.dto.request.StoreUpdateRequest;
import com.structure.springboot.features.store.dto.response.StoreResponse;
import com.structure.springboot.features.store.entity.StoreEntity;
import com.structure.springboot.features.store.mapper.StoreMapper;
import com.structure.springboot.features.store.repository.StoreRepository;
import com.structure.springboot.features.store.service.StoreService;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.infrastructure.persistence.StatusSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private static final Set<String> STORE_SORT_FIELDS = Set.of("storeName", "createdAt", "updatedAt");

    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;
    private final ManagedImageService managedImageService;

    @Override
    @Transactional
    public StoreResponse createStore(StoreRequest request) {
        String name = normalizeName(request.storeName());
        if (storeRepository.existsByStoreNameIgnoreCase(name)) {
            throw new ApiException(ErrorCode.CONFLICT, "Store already exists: " + name);
        }
        managedImageService.requireExternalImageUrl(request.imageUrl());

        StoreEntity entity = storeMapper.toEntity(request);
        entity.setStoreName(name);
        return storeMapper.toResponse(storeRepository.save(entity));
    }

    @Override
    @Transactional
    public StoreResponse updateStore(UUID id, StoreUpdateRequest request) {
        StoreEntity entity = requireManageable(id);

        // PATCH: field null nghĩa là "không đổi" nên mọi kiểm tra đều phải bọc null trước.
        String newName = request.storeName() == null ? null : normalizeName(request.storeName());
        if (newName != null && storeRepository.existsByStoreNameIgnoreCaseAndIdNot(newName, id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Store already exists: " + newName);
        }
        if (request.status() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Use DELETE to remove a store");
        }
        if (request.imageUrl() != null) {
            managedImageService.requireExternalImageUrl(request.imageUrl());
        }

        storeMapper.updateEntity(request, entity);
        if (newName != null) {
            entity.setStoreName(newName);
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        return storeMapper.toResponse(storeRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteStore(UUID id) {
        StoreEntity entity = requireManageable(id);
        entity.setStatus(RecordStatus.DELETED);
        storeRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> getPublicStores(Pageable pageable) {
        SortWhitelist.check(pageable.getSort(), STORE_SORT_FIELDS);
        return PageResponse.from(storeRepository
                .findAll(StatusSpecifications.<StoreEntity>visible(), pageable)
                .map(storeMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getPublicStoreById(UUID id) {
        return storeRepository.findById(id)
                .filter(StoreEntity::isVisible)
                .map(storeMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Store not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> getAdminStores(Pageable pageable) {
        SortWhitelist.check(pageable.getSort(), STORE_SORT_FIELDS);
        return PageResponse.from(storeRepository
                .findAll(StatusSpecifications.<StoreEntity>notDeleted(), pageable)
                .map(storeMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getAdminStoreById(UUID id) {
        return storeMapper.toResponse(requireManageable(id));
    }

    /** Cho phép cả bản ghi đang ẩn; đã xoá thì trả 404 vì với người gọi nó không còn tồn tại. */
    private StoreEntity requireManageable(UUID id) {
        Optional<StoreEntity> found = storeRepository.findById(id);
        StoreEntity entity = found.orElseThrow(
                () -> new ApiException(ErrorCode.NOT_FOUND, "Store not found with id: " + id));
        if (entity.getStatus() == RecordStatus.DELETED) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Store not found with id: " + id);
        }
        return entity;
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Store name is required");
        }
        return name.trim();
    }
}
