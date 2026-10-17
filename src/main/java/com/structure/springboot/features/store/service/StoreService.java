package com.structure.springboot.features.store.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.store.dto.request.StoreRequest;
import com.structure.springboot.features.store.dto.request.StoreUpdateRequest;
import com.structure.springboot.features.store.dto.response.StoreResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StoreService {

    StoreResponse createStore(StoreRequest request);

    StoreResponse updateStore(UUID id, StoreUpdateRequest request);

    /** Xoá MỀM: chi nhánh đóng cửa vẫn có thể bị nhắc tới về sau. */
    void deleteStore(UUID id);

    /** Khách: chỉ chi nhánh đang mở. */
    PageResponse<StoreResponse> getPublicStores(Pageable pageable);

    StoreResponse getPublicStoreById(UUID id);

    /** Admin: gồm cả chi nhánh đang ẩn, không gồm đã xoá. */
    PageResponse<StoreResponse> getAdminStores(Pageable pageable);

    StoreResponse getAdminStoreById(UUID id);
}
