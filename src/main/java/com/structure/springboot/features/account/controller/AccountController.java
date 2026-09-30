package com.structure.springboot.features.account.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.response.AccountResponse;
import com.structure.springboot.features.account.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody AccountRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<AccountResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Account created successfully")
                        .result(accountService.createAccount(requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody AccountRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<AccountResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Account updated successfully")
                        .result(accountService.updateAccount(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @PathVariable UUID id,
            HttpServletRequest request) {

        accountService.deleteAccount(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Account deleted successfully")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<AccountResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Account retrieved successfully")
                        .result(accountService.getAccountById(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts(
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<List<AccountResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Accounts retrieved successfully")
                        .result(accountService.getAllAccounts())
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
