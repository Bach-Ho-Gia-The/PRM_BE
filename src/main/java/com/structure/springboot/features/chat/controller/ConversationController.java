package com.structure.springboot.features.chat.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.chat.dto.request.ChatMessageRequest;
import com.structure.springboot.features.chat.dto.response.ChatMessageResponse;
import com.structure.springboot.features.chat.dto.response.ConversationResponse;
import com.structure.springboot.features.chat.dto.response.ConversationSummaryResponse;
import com.structure.springboot.features.chat.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nhắn tin giữa khách và shop.
 *
 * <p>Không dùng WebSocket: FE hỏi lại {@code /messages?since=...} mỗi vài giây khi màn chat đang
 * mở. Không có tin mới thì trả danh sách rỗng, rất nhẹ.</p>
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    // ──────────────── phía khách ────────────────

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ConversationResponse>> getMyConversation(
            @AuthenticationPrincipal AuthenticatedUser currentUser, HttpServletRequest request) {
        return one(conversationService.getOrCreateMyConversation(
                AuthenticatedUser.requireId(currentUser)), "Conversation retrieved successfully", request);
    }

    /** Lịch sử: phân trang, MỚI NHẤT TRƯỚC. Dùng khi mở màn chat và khi cuộn ngược lên. */
    @GetMapping("/me/messages")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> getMyMessageHistory(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @ParameterObject
            @PageableDefault(size = 20, direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest request) {
        return pageOk(conversationService.getMyMessageHistory(
                AuthenticatedUser.requireId(currentUser), pageable), request);
    }

    /**
     * Tin mới hơn {@code since}: MẢNG phẳng, CŨ TRƯỚC, không phân trang. Dùng cho vòng hỏi lại
     * mỗi vài giây khi màn chat đang mở.
     *
     * <p>Tách khỏi {@code /messages} chứ không gộp bằng tham số tuỳ chọn: hai thứ này khác nhau
     * cả kiểu trả về lẫn chiều sắp xếp. Gộp thì kiểu trả về phải khai là {@code ApiResponse<?>},
     * Swagger sinh tài liệu sai và FE không sinh được model.</p>
     */
    @GetMapping("/me/messages/new")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMyNewMessages(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            HttpServletRequest request) {
        return listOk(conversationService.getMyMessagesSince(
                AuthenticatedUser.requireId(currentUser), since), request);
    }

    @PostMapping("/me/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendAsCustomer(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ChatMessageRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ChatMessageResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Message sent successfully")
                        .result(conversationService.sendAsCustomer(
                                AuthenticatedUser.requireId(currentUser), body))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PatchMapping("/me/read")
    public ResponseEntity<ApiResponse<ConversationResponse>> markReadByCustomer(
            @AuthenticationPrincipal AuthenticatedUser currentUser, HttpServletRequest request) {
        return one(conversationService.markReadByCustomer(
                AuthenticatedUser.requireId(currentUser)), "Conversation marked as read", request);
    }

    // ──────────────── phía admin ────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<PageResponse<ConversationSummaryResponse>>> getAdminConversations(
            @ParameterObject
            @PageableDefault(size = 20, direction = Sort.Direction.DESC) Pageable pageable, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<ConversationSummaryResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Conversations retrieved successfully")
                        .result(conversationService.getAdminConversations(pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> countAwaitingAdmin(
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Long>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Unread count retrieved successfully")
                        .result(Map.of("unreadCount", conversationService.countConversationsAwaitingAdmin()))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}/messages")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> getAdminMessageHistory(
            @PathVariable UUID id,
            @ParameterObject
            @PageableDefault(size = 20, direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest request) {
        return pageOk(conversationService.getAdminMessageHistory(id, pageable), request);
    }

    /** Bản dành cho admin của {@code /me/messages/new}. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}/messages/new")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getAdminNewMessages(
            @PathVariable UUID id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            HttpServletRequest request) {
        return listOk(conversationService.getAdminMessagesSince(id, since), request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendAsAdmin(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID id, @Valid @RequestBody ChatMessageRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ChatMessageResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Reply sent successfully")
                        .result(conversationService.sendAsAdmin(
                                AuthenticatedUser.requireId(currentUser), id, body))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/{id}/read")
    public ResponseEntity<ApiResponse<ConversationSummaryResponse>> markReadByAdmin(
            @PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<ConversationSummaryResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Conversation marked as read")
                        .result(conversationService.markReadByAdmin(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    private ResponseEntity<ApiResponse<List<ChatMessageResponse>>> listOk(
            List<ChatMessageResponse> result, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<List<ChatMessageResponse>>builder()
                .code(HttpStatus.OK.value()).message("Messages retrieved successfully")
                .result(result).path(request.getRequestURI()).build());
    }

    private ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> pageOk(
            PageResponse<ChatMessageResponse> result, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<PageResponse<ChatMessageResponse>>builder()
                .code(HttpStatus.OK.value()).message("Messages retrieved successfully")
                .result(result).path(request.getRequestURI()).build());
    }

    private ResponseEntity<ApiResponse<ConversationResponse>> one(
            ConversationResponse result, String message, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<ConversationResponse>builder()
                .code(HttpStatus.OK.value()).message(message).result(result)
                .path(request.getRequestURI()).build());
    }
}
