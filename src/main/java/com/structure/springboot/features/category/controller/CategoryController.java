package com.structure.springboot.features.category.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.util.SortWhitelist;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.request.CategoryUpdateRequest;
import com.structure.springboot.features.category.dto.response.CategoryResponse;
import com.structure.springboot.features.category.service.CategoryService;
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
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CategoryResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Category created successfully")
                        .result(categoryService.createCategory(requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * PATCH = cập nhật MỘT PHẦN: field nào không gửi trong JSON thì giữ nguyên giá trị cũ.
     * Dùng PATCH thay PUT vì PUT theo chuẩn HTTP nghĩa là thay thế TOÀN BỘ tài nguyên.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryUpdateRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Category updated successfully")
                        .result(categoryService.updateCategory(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID id,
            HttpServletRequest request) {

        categoryService.deleteCategory(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Category deleted successfully")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Category retrieved successfully")
                        .result(categoryService.getCategoryById(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Phân trang qua query param {@code ?page=0&size=20&sort=createdAt,desc}.
     * {@code @PageableDefault} chốt sẵn cỡ trang và thứ tự để client không gửi gì vẫn có kết quả
     * ổn định — thiếu {@code sort} thì DB được tự do trả thứ tự tuỳ ý, trang 2 có thể lặp lại
     * bản ghi đã thấy ở trang 1.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> getAllCategories(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        SortWhitelist.check(pageable.getSort(), Set.of("name", "createdAt"));

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<CategoryResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Categories retrieved successfully")
                        .result(categoryService.getAllCategories(pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
