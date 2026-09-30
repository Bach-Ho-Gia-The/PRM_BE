package com.structure.springboot.features.role.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.features.role.dto.request.RoleRequest;
import com.structure.springboot.features.role.dto.response.RoleResponse;
import com.structure.springboot.features.role.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

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

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleRequest requestDTO,
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles(
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<List<RoleResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Roles retrieved successfully")
                        .result(roleService.getAllRoles())
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
