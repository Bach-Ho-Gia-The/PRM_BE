package com.structure.springboot.features.auth.dto.response;

/**
 * Kết quả của {@code POST /auth/register} — <b>chưa có tài khoản nào được tạo</b>, mới chỉ gửi mã.
 *
 * <p>{@code registrationId} là thứ frontend phải giữ lại và gửi kèm ở bước xác minh. Bấm "gửi
 * lại mã" sẽ nhận một {@code registrationId} MỚI, client phải ghi đè giá trị cũ.</p>
 *
 * <p>{@code expiresIn} và {@code resendAfter} trả về để frontend đếm ngược và khoá nút gửi lại
 * mà không phải viết cứng hai con số đang nằm trong cấu hình backend.</p>
 */
public record RegistrationPendingResponse(
        String registrationId,
        String email,
        long expiresIn,
        long resendAfter
) {
}
