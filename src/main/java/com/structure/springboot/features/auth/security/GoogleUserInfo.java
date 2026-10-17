package com.structure.springboot.features.auth.security;

/**
 * Phần thông tin lấy được từ ID token của Google sau khi đã xác minh.
 *
 * <p>Không phải DTO của API nên không nằm trong {@code dto/}: nó là kết quả nội bộ của
 * {@link GoogleTokenVerifier}, chỉ đi từ tầng security sang service và không bao giờ bị tuần tự
 * hoá ra ngoài.</p>
 */
public record GoogleUserInfo(
        String providerId,
        String email,
        String fullName,
        String avatarUrl
) {
}
