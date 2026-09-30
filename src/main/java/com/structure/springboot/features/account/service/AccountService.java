package com.structure.springboot.features.account.service;

import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.response.AccountResponse;

import java.util.List;
import java.util.UUID;

public interface AccountService {
    AccountResponse createAccount(AccountRequest request);
    AccountResponse updateAccount(UUID id, AccountRequest request);
    void deleteAccount(UUID id);
    AccountResponse getAccountById(UUID id);
    List<AccountResponse> getAllAccounts();
}
