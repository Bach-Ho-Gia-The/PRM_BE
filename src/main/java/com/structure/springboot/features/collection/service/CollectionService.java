package com.structure.springboot.features.collection.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.collection.dto.request.CollectionCreateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionProductsRequest;
import com.structure.springboot.features.collection.dto.request.CollectionUpdateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.dto.response.CollectionDetailResponse;
import com.structure.springboot.features.collection.dto.response.CollectionSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Các thao tác quản trị BST; API công khai sẽ được tách ở task 2C. */
public interface CollectionService {

    CollectionDetailResponse createCollection(CollectionCreateRequest request);
    CollectionDetailResponse updateCollection(UUID id, CollectionUpdateRequest request);
    CollectionDetailResponse replaceProducts(UUID id, CollectionProductsRequest request);
    CollectionDetailResponse publishCollection(UUID id, CollectionVersionRequest request);
    CollectionDetailResponse hideCollection(UUID id, CollectionVersionRequest request);
    void deleteCollection(UUID id, long expectedVersion);
    CollectionDetailResponse getCollectionById(UUID id);
    PageResponse<CollectionSummaryResponse> getAllCollections(Pageable pageable);
}
