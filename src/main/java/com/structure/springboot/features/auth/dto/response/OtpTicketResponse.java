package com.structure.springboot.features.auth.dto.response;

/**
 * Kết quả của {@code POST /auth/verify-otp}: vé tạm và số giây còn hiệu lực.
 *
 * <p>Trả {@code expiresIn} để frontend đếm ngược được mà không phải viết cứng con số đang nằm
 * trong cấu hình backend.</p>
 */
public record OtpTicketResponse(
        String ticket,
        long expiresIn
) {
}
