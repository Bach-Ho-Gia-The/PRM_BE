package com.structure.springboot.features.payment.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.payment.dto.response.PaymentResponse;
import com.structure.springboot.features.payment.dto.response.VnpayPaymentUrlResponse;
import com.structure.springboot.features.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Thanh toán — mọi endpoint ở đây đều <b>bắt buộc đăng nhập</b> và chỉ tác động lên đơn của chính
 * người đang đăng nhập (tiền tố {@code /me/}).
 *
 * <p>{@code /payments} <b>không</b> nằm trong {@code app.security.whitelist} nên
 * {@code SecurityConfig} tự chặn bằng {@code anyRequest().authenticated()}. Đường dẫn công khai
 * dành cho VNPay (IPN, Return) sẽ nằm ở controller riêng và thuộc đợt sau — cố ý không đặt chung
 * prefix với các endpoint có quyền, vì whitelist khớp theo đường dẫn và <b>không phân biệt HTTP
 * method</b>.</p>
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Mở link thanh toán VNPay cho một đơn <b>chuyển khoản</b> của chính mình.
     *
     * <p><b>POST chứ không phải GET:</b> lần gọi đầu tiên <b>ghi một bản ghi payment</b> xuống
     * database, nên nó không phải thao tác chỉ đọc. Những lần sau (đang có lần thử PENDING) chỉ
     * dựng lại URL từ bản ghi cũ — vẫn trả 200, nhưng không sinh bản ghi mới và <b>không gia hạn</b>
     * thời gian thanh toán.</p>
     */
    @PostMapping("/me/{orderNumber}/vnpay")
    public ResponseEntity<ApiResponse<VnpayPaymentUrlResponse>> startVnpayPayment(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String orderNumber,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<VnpayPaymentUrlResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Payment URL created successfully")
                        .result(paymentService.startVnpayPayment(
                                AuthenticatedUser.requireId(currentUser),
                                orderNumber,
                                resolveClientIp(request)))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Trạng thái thanh toán của một đơn của chính mình — khách và shop cùng thấy được. */
    @GetMapping("/me/{orderNumber}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getMyPayment(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String orderNumber,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PaymentResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Payment retrieved successfully")
                        .result(paymentService.getMyPayment(
                                AuthenticatedUser.requireId(currentUser), orderNumber))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Lấy IP của khách để gửi sang VNPay bằng {@code vnp_IpAddr}.
     *
     * <p>Xét {@code X-Forwarded-For} trước vì khi chạy sau tunnel/nginx, {@code getRemoteAddr()}
     * luôn là địa chỉ của proxy. Chuỗi {@code X-Forwarded-For} có thể chứa nhiều IP ngăn bằng dấu
     * phẩy — cái <b>đầu tiên</b> là máy khách thật.</p>
     *
     * <p><b>Lưu ý:</b> header do client tự gửi nên giá trị này <b>không đáng tin</b> để chống gian
     * lận. VNPay chỉ dùng {@code vnp_IpAddr} cho mục đích thống kê/rủi ro, không dùng để xác thực
     * giao dịch — việc xác thực nằm ở chữ ký. Nếu deploy thật sau proxy, cần cấu hình
     * {@code server.forward-headers-strategy} để container tự đọc header một cách có kiểm soát.</p>
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
