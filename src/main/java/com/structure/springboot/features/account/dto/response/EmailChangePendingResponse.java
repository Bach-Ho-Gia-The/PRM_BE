package com.structure.springboot.features.account.dto.response;

/**
 * Trả về sau bước 1 để client dựng màn nhập mã: còn bao lâu mã hết hạn và bao lâu nữa mới được
 * bấm "gửi lại". Cùng khuôn với {@code RegistrationPendingResponse} của luồng đăng ký.
 */
public record EmailChangePendingResponse(
        String newEmail,
        long expiresIn,
        long resendAfter
) {
}
