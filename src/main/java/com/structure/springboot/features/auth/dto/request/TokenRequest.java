package com.structure.springboot.features.auth.dto.request;

/**
 * Body dùng chung cho {@code /auth/refresh}, {@code /auth/logout} và {@code /auth/logout-all}
 * — cả ba đều chỉ cần đúng một thứ: một chuỗi token. Tách thành ba record giống hệt nhau chỉ
 * làm phình code mà không thêm thông tin nào.
 *
 * <p>Field tên {@code token} chứ không phải {@code refreshToken}: {@code /auth/logout} và
 * {@code /auth/logout-all} nhận <b>cả access token</b> (claim {@code sid} của nó đủ để xác định
 * phiên cần thu hồi), nên cái tên cũ mô tả sai một nửa số endpoint đang dùng record này.</p>
 *
 * <p><b>Không bắt buộc</b>: client web để token trong HttpOnly cookie nên gửi body rỗng cũng được;
 * client mobile không có cookie thì gửi qua field này.</p>
 */
public record TokenRequest(
        String token
) {
}
