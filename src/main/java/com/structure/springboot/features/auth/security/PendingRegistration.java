package com.structure.springboot.features.auth.security;

/**
 * Hồ sơ đăng ký đang chờ xác minh email, cất tạm trong Redis.
 *
 * <p>{@code passwordHash} đã qua BCrypt trước khi rời khỏi service — Redis không bao giờ giữ mật
 * khẩu thô, đúng mức an toàn như chuỗi nằm trong bảng {@code accounts}.</p>
 */
public record PendingRegistration(
        String email,
        String passwordHash,
        String fullName,
        String phone
) {
}
