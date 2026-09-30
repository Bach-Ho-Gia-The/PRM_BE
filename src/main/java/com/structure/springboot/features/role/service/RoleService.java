package com.structure.springboot.features.role.service;

import com.structure.springboot.features.role.dto.request.RoleRequest;
import com.structure.springboot.features.role.dto.response.RoleResponse;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    RoleResponse createRole(RoleRequest request);
    RoleResponse updateRole(UUID id, RoleRequest request);
    void deleteRole(UUID id);
    RoleResponse getRoleById(UUID id);
    List<RoleResponse> getAllRoles();
}
