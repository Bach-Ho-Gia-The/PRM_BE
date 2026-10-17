package com.structure.springboot.features.profile.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.profile.dto.request.ProfileUpdateRequest;
import com.structure.springboot.features.profile.dto.response.ProfileResponse;
import com.structure.springboot.features.profile.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Hồ sơ cá nhân — <b>chỉ thao tác được trên hồ sơ của chính mình</b>.
 *
 * <p>Người dùng thường chỉ thao tác được trên hồ sơ của chính mình — id luôn lấy từ token.
 * Riêng {@code GET /profiles/{accountId}} nhận id người khác nên khoá bằng {@code hasRole(ADMIN)}.</p>
 *
 * <p>{@code /profiles} không nằm trong {@code app.security.whitelist} nên
 * {@code anyRequest().authenticated()} tự chặn request không mang token.</p>
 */
@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProfileResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Profile retrieved successfully")
                        .result(profileService.getMyProfile(AuthenticatedUser.requireId(currentUser)))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Admin xem hồ sơ của một tài khoản bất kỳ.
     *
     * <p>Đặt sau {@code /me} không gây nhập nhằng định tuyến: Spring luôn ưu tiên chuỗi cố định
     * hơn biến đường dẫn, nên {@code /profiles/me} vẫn rơi vào đúng endpoint bên trên.</p>
     */
    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfileByAccountId(
            @PathVariable UUID accountId,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProfileResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Profile retrieved successfully")
                        .result(profileService.getByAccountId(accountId))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Avatar belongs to the authenticated account; there is no accountId/publicId in the request. */
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileResponse>> uploadMyAvatar(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<ProfileResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Profile avatar uploaded successfully")
                .result(profileService.uploadAvatar(AuthenticatedUser.requireId(currentUser), file))
                .path(request.getRequestURI())
                .build());
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ProfileUpdateRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProfileResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Profile updated successfully")
                        .result(profileService.updateMyProfile(AuthenticatedUser.requireId(currentUser), requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
