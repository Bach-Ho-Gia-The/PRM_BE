package com.structure.springboot.features.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Kết quả đăng nhập, dùng chung cho cả hai loại client.
 *
 * <p>Thông tin tài khoản được <b>làm phẳng</b> thành field (userId, email, roleName)
 * đúng theo quy ước của các response sẵn có trong dự án: {@code ProductResponse} flatten
 * {@code categoryId/categoryName}, {@code AccountResponse} flatten {@code roleName}.</p>
 *
 * <p>{@code accessToken}/{@code refreshToken} chỉ có giá trị ở chế độ <b>mobile</b>
 * ({@code X-Client-Type: mobile}). Ở chế độ <b>web</b> (mặc định) token nằm trong HttpOnly cookie
 * nên hai field này là null và được {@code NON_NULL} ẩn khỏi JSON — lưu ý {@code @JsonInclude}
 * của {@code ApiResponse} chỉ áp dụng cho field của chính nó, không lan xuống object trong
 * {@code result}, nên phải khai báo lại ở đây.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UUID userId,
        String email,
        String roleName
) {
}
