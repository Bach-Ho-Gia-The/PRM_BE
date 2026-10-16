package com.structure.springboot.common.security;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import org.springframework.security.core.AuthenticatedPrincipal;

import java.util.UUID;

/**
 * Danh tính người dùng nạp vào {@code SecurityContext} sau khi xác minh JWT.
 *
 * <p>Trước đây filter nhét chuỗi email làm principal rồi giấu {@code uid} vào
 * {@code authentication.setDetails(...)} — sai quy ước, vì {@code details} là chỗ Spring dành cho
 * thông tin kỹ thuật của request (IP, session id), không phải dữ liệu nghiệp vụ. Hệ quả là muốn
 * biết id người đăng nhập thì phải ép kiểu {@code (String) getDetails()}, không ai đoán ra.</p>
 *
 * <p>Cài {@link AuthenticatedPrincipal} nên {@code authentication.getName()} vẫn trả về email
 * đúng như cũ — không làm hỏng audit hay {@code @PreAuthorize} đang có. Controller về sau chỉ cần
 * {@code @AuthenticationPrincipal AuthenticatedUser user} là có luôn cả {@code id} lẫn email,
 * khỏi bóc token lần nữa.</p>
 */
public record AuthenticatedUser(UUID id, String email) implements AuthenticatedPrincipal {

    /**
     * Mọi token do {@code JwtService} phát ra đều có {@code uid} hợp lệ, nên thiếu hoặc sai định dạng
     * nghĩa là token bị chắp vá — từ chối luôn thay vì cho đi tiếp với {@code id = null} rồi vỡ
     * ở một chỗ nào đó xa hơn, khó lần ra nguyên nhân.
     */
    public static AuthenticatedUser of(String uid, String email) {
        try {
            return new AuthenticatedUser(UUID.fromString(uid), email);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Malformed token");
        }
    }

    /**
     * Lấy id người đang đăng nhập, chặn tường minh trường hợp principal null.
     *
     * <p>Đặt ở đây thay vì nhân bản một hàm private giống hệt trong từng controller: mọi endpoint
     * {@code /me} đều cần đúng một phép kiểm tra này. Principal null nghĩa là request đi lọt tới
     * controller mà chưa qua xác thực (ví dụ {@code /accounts/**} còn trong whitelist — nợ N1);
     * trả 401 đúng format {@code ApiResponse} thay vì vỡ thành NullPointerException 500.</p>
     */
    public static UUID requireId(AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return currentUser.id();
    }

    @Override
    public String getName() {
        return email;
    }
}
