package com.structure.springboot.features.payment.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.payment.dto.response.PaymentResponse;
import com.structure.springboot.features.payment.dto.response.RefundAdminResponse;
import com.structure.springboot.features.payment.dto.response.VnpayPaymentUrlResponse;
import com.structure.springboot.features.payment.enums.RefundStatus;
import com.structure.springboot.features.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
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
     * Màn quản trị: danh sách khoản hoàn tiền, lọc theo trạng thái nếu truyền {@code ?status=}.
     *
     * <p><b>Chỉ {@code ADMIN}.</b> Đặt ở {@code /payments/admin/refunds}: tiền tố {@code admin} tách
     * bạch với khe của khách ({@code /me/**}) và khe công khai VNPay ({@code /vnpay/**}).</p>
     *
     * <p>Mặc định mới nhất trước ({@code createdAt DESC}); phân trang 20 dòng/trang.</p>
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/refunds")
    public ResponseEntity<ApiResponse<PageResponse<RefundAdminResponse>>> getRefunds(
            @RequestParam(required = false) RefundStatus status,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<RefundAdminResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Refunds retrieved successfully")
                        .result(paymentService.getRefunds(status, pageable))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Shop xác nhận đã <b>thu tiền mặt</b> cho một đơn COD.
     *
     * <p><b>Chỉ {@code ADMIN}.</b> Khác hẳn các endpoint {@code /me/...}: đây là thao tác của shop
     * trên đơn của khách, không lọc theo người đang đăng nhập — nên <b>không</b> nhận
     * {@code @AuthenticationPrincipal} để scope theo chủ sở hữu.</p>
     *
     * <p><b>POST:</b> đây là thao tác ghi (đổi payment sang {@code PAID}). Idempotent — bấm nhiều
     * lần trên đơn đã thu tiền trả về cùng kết quả và không dời mốc {@code paidAt}.</p>
     *
     * <p>Đặt dưới tiền tố {@code /cod/} chứ không phải {@code /me/} hay {@code /vnpay/}: segment
     * {@code cod} là literal, tách bạch với khe công khai {@code /payments/vnpay/**} và với khe
     * của khách {@code /payments/me/**}.</p>
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cod/{orderNumber}/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmCodPayment(
            @PathVariable String orderNumber,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<PaymentResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("COD payment marked as collected")
                        .result(paymentService.confirmCodPayment(orderNumber))
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
