package com.structure.springboot.features.product.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.util.SortWhitelist;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.dto.response.ProductResponse;
import com.structure.springboot.features.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
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

    /**
     * PATCH = cập nhật MỘT PHẦN: field nào không gửi trong JSON thì giữ nguyên giá trị cũ.
     * Dùng PATCH thay PUT vì PUT theo chuẩn HTTP nghĩa là thay thế TOÀN BỘ tài nguyên.
     */
    @PatchMapping("/{id}")
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

    /** Xoá MỀM — bản ghi vẫn còn trong DB để đơn hàng cũ không bị mồ côi, chỉ là không ai đọc được nữa. */
    @DeleteMapping("/{id}")
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

    @GetMapping("/{id}")
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
     * <p>Đặt dưới tiền tố {@code /slug} thay vì để chung {@code /products/{idOrSlug}}: hai kiểu
     * định danh khác nhau thì nên có hai đường riêng, Spring khỏi phải đoán chuỗi nhận được là
     * UUID hay slug, và {@code @PathVariable UUID} vẫn tự validate được định dạng.</p>
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
     * Danh sách sản phẩm cho trang danh mục.
     *
     * <p>Lọc: {@code ?category=ao&collection=SUNSET&search=lua&minPrice=300000&maxPrice=700000&featured=true}.
     * Phân trang và sắp xếp: {@code ?page=0&size=20&sort=price,asc}. Mọi bộ lọc đều tuỳ chọn.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @ParameterObject ProductFilter filter,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        // Danh sách để thẳng tại đây thay vì tách hằng: dùng đúng một lần, và đọc phát là biết
        // ngay endpoint này cho sắp xếp theo gì mà không phải nhảy sang file khác.
        SortWhitelist.check(pageable.getSort(), Set.of("name", "price", "createdAt", "updatedAt"));

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
