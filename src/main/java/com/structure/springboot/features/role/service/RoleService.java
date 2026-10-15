package com.structure.springboot.features.role.service;

import com.structure.springboot.features.role.dto.request.RoleRequest;
import com.structure.springboot.features.role.dto.request.RoleUpdateRequest;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.role.dto.response.RoleResponse;

import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface RoleService {
    RoleResponse createRole(RoleRequest request);
    RoleResponse updateRole(UUID id, RoleUpdateRequest request);
    void deleteRole(UUID id);
    RoleResponse getRoleById(UUID id);
    PageResponse<RoleResponse> getAllRoles(Pageable pageable);
}
