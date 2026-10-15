package com.structure.springboot.features.order.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.order.dto.request.OrderRequest;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}
