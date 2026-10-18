package com.structure.springboot.features.store.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.store.dto.request.StoreRequest;
import com.structure.springboot.features.store.dto.request.StoreUpdateRequest;
import com.structure.springboot.features.store.dto.response.StoreResponse;
import com.structure.springboot.features.store.service.StoreService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Chi nhánh cửa hàng cho màn bản đồ. Toạ độ do backend cấp, FE không hardcode. */
@RestController
@RequestMapping("/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StoreResponse>>> getPublicStores(
            @ParameterObject
            @PageableDefault(size = 20, sort = "storeName", direction = Sort.Direction.ASC)
            Pageable pageable, HttpServletRequest request) {
        return ok(storeService.getPublicStores(pageable), "Stores retrieved successfully", request);
    }

    /** Đường dẫn {@code /id/{id}} chứ không {@code /{id}}: {@code /stores/*} trong whitelist sẽ
     * khớp luôn {@code /stores/admin}. Cùng cách đặt với {@code /categories/id/{id}}. */
    @GetMapping("/id/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> getPublicStoreById(
            @PathVariable UUID id, HttpServletRequest request) {
        return okOne(storeService.getPublicStoreById(id), "Store retrieved successfully", request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<PageResponse<StoreResponse>>> getAdminStores(
            @ParameterObject
            @PageableDefault(size = 20, sort = "storeName", direction = Sort.Direction.ASC)
            Pageable pageable, HttpServletRequest request) {
        return ok(storeService.getAdminStores(pageable), "Stores retrieved successfully", request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin")
    public ResponseEntity<ApiResponse<StoreResponse>> createStore(
            @Valid @RequestBody StoreRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<StoreResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Store created successfully")
                        .result(storeService.createStore(body))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> getAdminStoreById(
            @PathVariable UUID id, HttpServletRequest request) {
        return okOne(storeService.getAdminStoreById(id), "Store retrieved successfully", request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @PathVariable UUID id, @Valid @RequestBody StoreUpdateRequest body,
            HttpServletRequest request) {
        return okOne(storeService.updateStore(id, body), "Store updated successfully", request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStore(
            @PathVariable UUID id, HttpServletRequest request) {
        storeService.deleteStore(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Store deleted successfully")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<StoreResponse>>> ok(
            PageResponse<StoreResponse> result, String message, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<PageResponse<StoreResponse>>builder()
                .code(HttpStatus.OK.value()).message(message).result(result)
                .path(request.getRequestURI()).build());
    }

    private ResponseEntity<ApiResponse<StoreResponse>> okOne(
            StoreResponse result, String message, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<StoreResponse>builder()
                .code(HttpStatus.OK.value()).message(message).result(result)
                .path(request.getRequestURI()).build());
    }
}
