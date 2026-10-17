package com.structure.springboot.features.product.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductThumbnailRequest;
import com.structure.springboot.features.product.dto.request.ProductVariantRequest;
import com.structure.springboot.features.product.dto.request.ProductVariantUpdateRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.dto.response.ProductResponse;
import com.structure.springboot.features.product.dto.response.ProductVariantResponse;
import com.structure.springboot.features.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Một controller cho cả /products/admin và các GET công khai, không đổi dữ liệu theo vai trò. */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody ProductRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ProductDetailResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Product created successfully")
                        .result(productService.createProduct(requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** PATCH cập nhật một phần: không gửi field thì giữ nguyên, không thay cả Product. */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductUpdateRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProductDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Product updated successfully")
                        .result(productService.updateProduct(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Upload a gallery image; first image becomes thumbnail if none is selected. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/admin/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> addImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean asThumbnail,
            HttpServletRequest request) {
        return imageResponse(productService.addImage(id, file, asThumbnail),
                "Product image uploaded successfully", HttpStatus.CREATED, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(value = "/admin/{id}/images/{imageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> replaceImage(
            @PathVariable UUID id, @PathVariable UUID imageId,
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return imageResponse(productService.replaceImage(id, imageId, file),
                "Product image replaced successfully", HttpStatus.OK, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}/images/{imageId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> removeImage(
            @PathVariable UUID id, @PathVariable UUID imageId, HttpServletRequest request) {
        return imageResponse(productService.removeImage(id, imageId),
                "Product image removed successfully", HttpStatus.OK, request);
    }

    /** Upload a new thumbnail, replacing the selected gallery row when there is one. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(value = "/admin/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> uploadThumbnail(
            @PathVariable UUID id, @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        return imageResponse(productService.uploadThumbnail(id, file),
                "Product thumbnail uploaded successfully", HttpStatus.OK, request);
    }

    /** Choose an existing gallery image without uploading/deleting anything. */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/{id}/thumbnail")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> selectThumbnail(
            @PathVariable UUID id, @Valid @RequestBody ProductThumbnailRequest body,
            HttpServletRequest request) {
        return imageResponse(productService.selectThumbnail(id, body),
                "Product thumbnail selected successfully", HttpStatus.OK, request);
    }

    /** Thêm một size + tồn kho. Sản phẩm không có size nào thì khách không đặt mua được. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/variants")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> addVariant(
            @PathVariable UUID id, @Valid @RequestBody ProductVariantRequest body,
            HttpServletRequest request) {
        return imageResponse(productService.addVariant(id, body),
                "Product variant added successfully", HttpStatus.CREATED, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/{id}/variants/{variantId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateVariant(
            @PathVariable UUID id, @PathVariable UUID variantId,
            @Valid @RequestBody ProductVariantUpdateRequest body, HttpServletRequest request) {
        return imageResponse(productService.updateVariant(id, variantId, body),
                "Product variant updated successfully", HttpStatus.OK, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}/variants/{variantId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> removeVariant(
            @PathVariable UUID id, @PathVariable UUID variantId, HttpServletRequest request) {
        return imageResponse(productService.removeVariant(id, variantId),
                "Product variant removed successfully", HttpStatus.OK, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}/variants")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getAdminVariants(
            @PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<List<ProductVariantResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Product variants retrieved successfully")
                        .result(productService.getAdminVariants(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    private ResponseEntity<ApiResponse<ProductDetailResponse>> imageResponse(
            ProductDetailResponse result, String message, HttpStatus status, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.<ProductDetailResponse>builder()
                .code(status.value()).message(message).result(result)
                .path(request.getRequestURI()).build());
    }

    /** Xoá MỀM: giữ Product để không phá lịch sử đơn hàng. */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable UUID id,
            HttpServletRequest request) {

        productService.deleteProduct(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Product deleted successfully")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getAdminProductById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProductDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Product retrieved successfully")
                        .result(productService.getAdminProductById(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAdminProducts(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<ProductResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Products retrieved successfully")
                        .result(productService.getAdminProducts(pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Tra Product công khai theo ID; tiền tố /id tách khỏi /admin và /slug. */
    @GetMapping("/id/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProductDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Product retrieved successfully")
                        .result(productService.getProductById(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Tra theo slug cho trang {@code /san-pham/{slug}} của frontend.
     *
     * <p>Đặt dưới /slug thay vì /products/{idOrSlug}: hai kiểu định danh có đường riêng.</p>
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductBySlug(
            @PathVariable String slug,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProductDetailResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Product retrieved successfully")
                        .result(productService.getProductBySlug(slug))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Danh sách Product: {@code ?category=ao&collection=sunset&search=lua&minPrice=300000}
     * có thể kết hợp {@code maxPrice}, {@code featured}, page/size/sort.
     * Collection nhận slug BST ACTIVE thật; thứ tự admin của BST nằm tại /collections/slug/{slug}.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @ParameterObject ProductFilter filter,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<ProductResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Products retrieved successfully")
                        .result(productService.getAllProducts(filter, pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
