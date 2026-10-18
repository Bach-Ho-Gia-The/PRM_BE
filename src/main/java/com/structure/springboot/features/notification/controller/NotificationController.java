package com.structure.springboot.features.notification.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.notification.dto.response.NotificationResponse;
import com.structure.springboot.features.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Thông báo của chính người đang đăng nhập. Không có đường nào đọc của người khác. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @ParameterObject
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<NotificationResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Notifications retrieved successfully")
                        .result(notificationService.getMyNotifications(
                                AuthenticatedUser.requireId(currentUser), unreadOnly, pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Cho chấm đỏ trên icon chuông. */
    @GetMapping("/me/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> countMyUnread(
            @AuthenticationPrincipal AuthenticatedUser currentUser, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Long>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Unread count retrieved successfully")
                        .result(Map.of("unreadCount",
                                notificationService.countMyUnread(AuthenticatedUser.requireId(currentUser))))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PatchMapping("/me/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<NotificationResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Notification marked as read")
                        .result(notificationService.markRead(AuthenticatedUser.requireId(currentUser), id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PatchMapping("/me/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(
            @AuthenticationPrincipal AuthenticatedUser currentUser, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Integer>>builder()
                        .code(HttpStatus.OK.value())
                        .message("All notifications marked as read")
                        .result(Map.of("updatedCount",
                                notificationService.markAllRead(AuthenticatedUser.requireId(currentUser))))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @DeleteMapping("/me/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMine(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID id, HttpServletRequest request) {
        notificationService.deleteMine(AuthenticatedUser.requireId(currentUser), id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Notification deleted successfully")
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
