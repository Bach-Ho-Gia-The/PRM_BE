package com.structure.springboot.features.account.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.request.AccountSelfUpdateRequest;
import com.structure.springboot.features.account.dto.request.AccountStatusUpdateRequest;
import com.structure.springboot.features.account.dto.request.AccountUpdateRequest;
import com.structure.springboot.features.account.dto.request.ChangePasswordRequest;
import com.structure.springboot.features.account.dto.response.AccountResponse;
import com.structure.springboot.features.account.service.AccountService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PreAuthorize("hasRole('ADMIN')")
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

    /**
     * PATCH = cập nhật MỘT PHẦN: field nào không gửi trong JSON thì giữ nguyên giá trị cũ.
     * Dùng PATCH thay PUT vì PUT theo chuẩn HTTP nghĩa là thay thế TOÀN BỘ tài nguyên.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody AccountUpdateRequest requestDTO,
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

    /**
     * Hồ sơ của chính người đang đăng nhập — id lấy từ token nên client không cần (và không được)
     * tự truyền id vào.
     *
     * <p><b>Vì sao FE cần endpoint này thay vì gọi {@code GET /accounts/{id}}:</b> ở chế độ web,
     * token nằm trong cookie HttpOnly nên JavaScript không đọc được, FE không có cách nào biết id
     * của chính mình. Còn việc tự giải mã JWT ở phía client để moi id ra là sai nguyên tắc —
     * payload JWT chỉ được ký chứ không được mã hoá, client không có quyền tin nội dung bên trong.</p>
     *
     * <p><b>Chỉ trả phần định danh</b> (email, role, mốc thời gian). Tên thật, số điện
     * thoại, ngày sinh, ảnh đại diện nằm ở {@code GET /profiles/me} — hai thứ khác nhau nên để
     * hai endpoint khác nhau.</p>
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AccountResponse>> getCurrentAccount(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<AccountResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Current account retrieved successfully")
                        .result(accountService.getAccountById(AuthenticatedUser.requireId(currentUser)))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Người dùng tự sửa username/email của CHÍNH MÌNH.
     *
     * <p>Dùng {@code AccountSelfUpdateRequest} thay vì {@code AccountUpdateRequest} vì bản kia có
     * {@code roleId} — để lọt vào đây là ai cũng tự nâng mình lên ADMIN được.</p>
     *
     * <p><b>Đổi email thì mọi phiên bị thu hồi</b>, kể cả phiên vừa gọi API này: mốc đăng xuất
     * được cắm theo email, giữ phiên cũ lại là giữ luôn một token không còn đối chiếu được.</p>
     */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<AccountResponse>> updateMyAccount(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody AccountSelfUpdateRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<AccountResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Account updated successfully")
                        .result(accountService.updateMyAccount(AuthenticatedUser.requireId(currentUser), requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Người dùng tự đổi mật khẩu của CHÍNH MÌNH — id lấy từ token, không nhận từ client, nên
     * không ai đổi được mật khẩu người khác dù có đoán trúng id.
     *
     * <p><b>Vì sao đặt ở {@code /accounts/me/password} chứ không phải {@code /auth/change-password}:</b>
     * {@code JwtAuthenticationFilter.shouldNotFilter()} cố ý bỏ qua toàn bộ {@code /auth/**}
     * (đăng nhập/refresh phải vào được khi chưa có token hợp lệ). Đặt endpoint này ở đó thì filter
     * không chạy ⇒ SecurityContext rỗng ⇒ {@code @AuthenticationPrincipal} luôn null.</p>
     *
     * <p>Đổi xong mọi phiên bị thu hồi, kể cả phiên vừa gọi API này ⇒ client phải đăng nhập lại
     * và nên xoá cookie/token đang giữ.</p>
     */
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ChangePasswordRequest requestDTO,
            HttpServletRequest request) {

        accountService.changePassword(AuthenticatedUser.requireId(currentUser), requestDTO);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Password changed successfully. Please sign in again.")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Khoá, mở khoá hoặc vô hiệu hoá một tài khoản. Đổi trạng thái là tài nguyên con chứ không
     * phải một trường của {@code PATCH /accounts/{id}} — cùng quy ước với {@code /orders/{id}/status}.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccountStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AccountStatusUpdateRequest requestDTO,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<AccountResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Account status updated successfully")
                        .result(accountService.updateStatus(
                                id, AuthenticatedUser.requireId(currentUser), requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
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

    @PreAuthorize("hasRole('ADMIN')")
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

    /**
     * Phân trang qua query param {@code ?page=0&size=20&sort=createdAt,desc}.
     * {@code @PageableDefault} chốt sẵn cỡ trang và thứ tự để client không gửi gì vẫn có kết quả
     * ổn định — thiếu {@code sort} thì DB được tự do trả thứ tự tuỳ ý, trang 2 có thể lặp lại
     * bản ghi đã thấy ở trang 1.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AccountResponse>>> getAllAccounts(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<AccountResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Accounts retrieved successfully")
                        .result(accountService.getAllAccounts(pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
