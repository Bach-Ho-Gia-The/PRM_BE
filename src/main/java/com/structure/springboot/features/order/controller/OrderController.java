package com.structure.springboot.features.order.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.dto.request.OrderStatusUpdateRequest;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.dto.response.OrderResponse;
import com.structure.springboot.features.order.dto.response.OrderSummaryResponse;
import com.structure.springboot.features.order.service.OrderService;
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

/**
 * Đơn hàng — <b>mọi endpoint đều bắt buộc đăng nhập</b>.
 *
 * <p>{@code /orders} không nằm trong {@code app.security.whitelist} nên {@code SecurityConfig}
 * tự chặn bằng {@code anyRequest().authenticated()}; request không token bị trả 401 từ trước khi
 * chạm tới controller.</p>
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody OrderRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<OrderResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Order placed successfully")
                        .result(orderService.createOrder(AuthenticatedUser.requireId(currentUser), requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Danh sách đơn của chính mình — dạng rút gọn, không kèm danh sách món. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getMyOrders(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<OrderSummaryResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Orders retrieved successfully")
                        .result(orderService.getMyOrders(AuthenticatedUser.requireId(currentUser), pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Chi tiết một đơn của chính mình, tra theo mã đơn in trên hoá đơn.
     *
     * <p>Đặt dưới {@code /orders/me/...} chứ không phải {@code /orders/{orderNumber}} để đường dẫn
     * tự nói lên phạm vi: đây là tài nguyên của người đang đăng nhập. Endpoint tra đơn bất kỳ
     * dành cho admin sẽ là {@code GET /orders/{id}}, làm ở đợt sau.</p>
     */
    @GetMapping("/me/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderByNumber(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String orderNumber,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Order retrieved successfully")
                        .result(orderService.getMyOrderByNumber(AuthenticatedUser.requireId(currentUser), orderNumber))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Màn quản trị: toàn bộ đơn của mọi khách, lọc theo trạng thái nếu truyền {@code ?status=}.
     *
     * <p>Đặt ở {@code GET /orders} chứ không phải {@code /orders/all}: tài nguyên vẫn là đơn hàng,
     * cái khác chỉ là phạm vi — còn {@code /orders/me} mới là lát cắt hẹp của chính nó.</p>
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<OrderSummaryResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Orders retrieved successfully")
                        .result(orderService.getOrders(status, pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Màn quản trị: chi tiết một đơn bất kỳ. Khách tự xem đơn mình thì dùng {@code /orders/me/{orderNumber}}. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Order retrieved successfully")
                        .result(orderService.getOrderById(id))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Admin chuyển trạng thái đơn. Chuyển sang CANCELLED thì hàng được trả về kho. */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Order status updated successfully")
                        .result(orderService.updateStatus(id, requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
