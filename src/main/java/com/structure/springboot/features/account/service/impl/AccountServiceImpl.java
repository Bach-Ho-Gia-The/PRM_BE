package com.structure.springboot.features.account.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.response.AccountResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.mapper.AccountMapper;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.account.service.AccountService;
import com.structure.springboot.features.role.entity.RoleEntity;
import com.structure.springboot.features.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        if (accountRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ApiException(ErrorCode.CONFLICT, "Account username already exists: " + request.username());
        }
        if (accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(ErrorCode.CONFLICT, "Account email already exists: " + request.email());
        }

        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + request.roleId()));

        AccountEntity entity = accountMapper.toEntity(request);
        entity.setRole(role);
        entity.setPassword(passwordEncoder.encode(request.password()));

        return accountMapper.toResponse(accountRepository.save(entity));
    }

    @Override
    @Transactional
    public AccountResponse updateAccount(UUID id, AccountRequest request) {
        AccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));

        if (accountRepository.existsByUsernameIgnoreCaseAndIdNot(request.username(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Account username already exists: " + request.username());
        }
        if (accountRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Account email already exists: " + request.email());
        }

        if (!entity.getRole().getId().equals(request.roleId())) {
            RoleEntity newRole = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found with id: " + request.roleId()));
            entity.setRole(newRole);
        }

        accountMapper.updateEntity(request, entity);
        entity.setPassword(passwordEncoder.encode(request.password()));

        return accountMapper.toResponse(accountRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteAccount(UUID id) {
        AccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));

        accountRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id) {
        return accountRepository.findById(id)
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(accountMapper::toResponse)
                .toList();
    }
}
