package com.structure.springboot.features.role.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.role.dto.request.RoleRequest;
import com.structure.springboot.features.role.dto.request.RoleUpdateRequest;
import com.structure.springboot.features.role.dto.response.RoleResponse;
import com.structure.springboot.features.role.service.RoleService;
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

import java.util.UUID;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody RoleRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<RoleResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Role created successfully")
                        .result(roleService.createRole(requestDTO))
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
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleUpdateRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<RoleResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Role updated successfully")
                        .result(roleService.updateRole(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable UUID id,
            HttpServletRequest request) {

        roleService.deleteRole(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Role deleted successfully")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<RoleResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Role retrieved successfully")
                        .result(roleService.getRoleById(id))
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
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> getAllRoles(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<RoleResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Roles retrieved successfully")
                        .result(roleService.getAllRoles(pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
