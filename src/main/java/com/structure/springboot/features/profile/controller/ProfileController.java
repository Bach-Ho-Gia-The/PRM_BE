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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Hồ sơ cá nhân — <b>chỉ thao tác được trên hồ sơ của chính mình</b>.
 *
 * <p>Không có endpoint nhận id người khác: id luôn lấy từ token. Endpoint cho admin xem hồ sơ
 * người dùng bất kỳ để dành Đợt 4, lúc đó mới có phân quyền theo vai trò.</p>
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
