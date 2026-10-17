package com.structure.springboot.features.category.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.request.CategoryUpdateRequest;
import com.structure.springboot.features.category.dto.response.CategoryPublicResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Một controller: quản trị dưới /categories/admin, GET cửa hàng dưới các URL riêng. */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin")
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

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/{id}")
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
    @DeleteMapping("/admin/{id}")
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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getAdminCategoryById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Category retrieved successfully")
                        .result(categoryService.getAdminCategoryById(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> getAdminCategories(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<CategoryResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Categories retrieved successfully")
                        .result(categoryService.getAdminCategories(pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** ID có tiền tố riêng để YAML không cần whitelist /categories/** (kể cả /admin). */
    @GetMapping("/id/{id}")
    public ResponseEntity<ApiResponse<CategoryPublicResponse>> getPublicCategoryById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<CategoryPublicResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Category retrieved successfully")
                        .result(categoryService.getPublicCategoryById(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<CategoryPublicResponse>> getPublicCategoryBySlug(
            @PathVariable String slug,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<CategoryPublicResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Category retrieved successfully")
                        .result(categoryService.getPublicCategoryBySlug(slug))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Public GET luôn chỉ trả Category ACTIVE, bất kể người gọi là guest, USER hay ADMIN. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CategoryPublicResponse>>> getPublicCategories(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<CategoryPublicResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Categories retrieved successfully")
                        .result(categoryService.getPublicCategories(pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
