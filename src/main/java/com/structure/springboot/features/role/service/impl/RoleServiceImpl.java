package com.structure.springboot.features.role.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.role.dto.request.RoleRequest;
import com.structure.springboot.features.role.dto.request.RoleUpdateRequest;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.role.dto.response.RoleResponse;
import com.structure.springboot.features.role.entity.RoleEntity;
import com.structure.springboot.features.role.mapper.RoleMapper;
import com.structure.springboot.features.role.repository.RoleRepository;
import com.structure.springboot.features.role.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
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
    public RoleResponse updateRole(UUID id, RoleUpdateRequest request) {
        RoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + id));

        // PATCH: field null nghĩa là "không đổi" nên bọc null trước khi kiểm tra trùng tên.
        if (request.roleName() != null && roleRepository.existsByRoleNameIgnoreCaseAndIdNot(request.roleName(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Role already exists: " + request.roleName());
        }

        roleMapper.updateEntity(request, entity);

        return roleMapper.toResponse(roleRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteRole(UUID id) {
        RoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + id));

        // Quan hệ Role -> Account không còn cascade, nên phải tự chặn: xoá role đang được dùng
        // sẽ làm Postgres văng lỗi khoá ngoại (500). Kiểm tra trước để trả 409 có thông điệp rõ ràng.
        if (accountRepository.existsByRoleId(id)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot delete role that is still assigned to accounts: " + entity.getRoleName());
        }

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
    public PageResponse<RoleResponse> getAllRoles(Pageable pageable) {
        // map() của Page giữ nguyên thông tin phân trang, chỉ đổi kiểu phần tử —
        // không được .getContent().stream() vì làm vậy là mất totalElements/totalPages.
        return PageResponse.from(roleRepository.findAll(pageable).map(roleMapper::toResponse));
    }
}
