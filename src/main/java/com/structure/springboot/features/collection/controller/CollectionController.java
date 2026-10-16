package com.structure.springboot.features.collection.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.util.SortWhitelist;
import com.structure.springboot.features.collection.dto.request.CollectionCreateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionProductsRequest;
import com.structure.springboot.features.collection.dto.request.CollectionUpdateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.dto.response.CollectionDetailResponse;
import com.structure.springboot.features.collection.dto.response.CollectionSummaryResponse;
import com.structure.springboot.features.collection.service.CollectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/** Một route /collections chung; 2B chỉ thêm method ADMIN, public sẽ bàn riêng ở 2C. */
@RestController
@RequestMapping("/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<CollectionDetailResponse>> createCollection(
            @Valid @RequestBody CollectionCreateRequest requestDTO,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CollectionDetailResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Collection created successfully")
                        .result(collectionService.createCollection(requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** PATCH chỉ sửa metadata của BST HIDDEN; clearCoverImage=true gỡ ảnh bìa. */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CollectionDetailResponse>> updateCollection(
            @PathVariable UUID id,
            @Valid @RequestBody CollectionUpdateRequest requestDTO,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<CollectionDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Collection updated successfully")
                        .result(collectionService.updateCollection(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Thay toàn bộ danh sách theo thứ tự; không lưu từng thao tác kéo thả rời rạc. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/products")
    public ResponseEntity<ApiResponse<CollectionDetailResponse>> replaceProducts(
            @PathVariable UUID id,
            @Valid @RequestBody CollectionProductsRequest requestDTO,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<CollectionDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Collection products updated successfully")
                        .result(collectionService.replaceProducts(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<CollectionDetailResponse>> publishCollection(
            @PathVariable UUID id,
            @Valid @RequestBody CollectionVersionRequest requestDTO,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<CollectionDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Collection published successfully")
                        .result(collectionService.publishCollection(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<CollectionDetailResponse>> hideCollection(
            @PathVariable UUID id,
            @Valid @RequestBody CollectionVersionRequest requestDTO,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<CollectionDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Collection hidden successfully")
                        .result(collectionService.hideCollection(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** DELETE không có body; version dạng query param để dùng được với mọi HTTP client. */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCollection(
            @PathVariable UUID id,
            @RequestParam(name = "expectedVersion") long expectedVersion,
            HttpServletRequest request) {
        collectionService.deleteCollection(id, expectedVersion);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Collection deleted successfully")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CollectionDetailResponse>> getCollectionById(
            @PathVariable UUID id,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<CollectionDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Collection retrieved successfully")
                        .result(collectionService.getCollectionById(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CollectionSummaryResponse>>> getAllCollections(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {
        SortWhitelist.check(pageable.getSort(), Set.of("collectionName", "slug", "createdAt", "updatedAt"));
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<CollectionSummaryResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Collections retrieved successfully")
                        .result(collectionService.getAllCollections(pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
