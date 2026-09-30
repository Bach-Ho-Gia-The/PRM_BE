package com.structure.springboot.features.role.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.role.dto.request.RoleRequest;
import com.structure.springboot.features.role.dto.response.RoleResponse;
import com.structure.springboot.features.role.entity.RoleEntity;
import com.structure.springboot.features.role.mapper.RoleMapper;
import com.structure.springboot.features.role.repository.RoleRepository;
import com.structure.springboot.features.role.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    @Override
    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.existsByRoleNameIgnoreCase(request.roleName())) {
            throw new ApiException(ErrorCode.CONFLICT, "Role already exists: " + request.roleName());
        }

        RoleEntity entity = roleMapper.toEntity(request);
        return roleMapper.toResponse(roleRepository.save(entity));
    }

    @Override
    @Transactional
    public RoleResponse updateRole(UUID id, RoleRequest request) {
        RoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + id));

        if (roleRepository.existsByRoleNameIgnoreCaseAndIdNot(request.roleName(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Role already exists: " +  request.roleName());
        }

        roleMapper.updateEntity(request, entity);

        return roleMapper.toResponse(roleRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteRole(UUID id) {
        RoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + id));

        roleRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(UUID id) {
        return roleRepository.findById(id)
                .map(roleMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(roleMapper::toResponse)
                .toList();
    }
}
