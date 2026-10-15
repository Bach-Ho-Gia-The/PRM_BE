package com.structure.springboot.features.account.enums;

/**
 * Trạng thái tài khoản — quyết định có được đăng nhập hay không.
 *
 * <p>{@code AccountPrincipal} ánh xạ sang 4 cờ của {@code UserDetails}, nên
 * {@code DaoAuthenticationProvider} tự chặn ngay từ bước xác thực, không service nào phải tự kiểm.</p>
 */
public enum AccountStatus {

    /** Bình thường, đăng nhập được. */
    ACTIVE,

    /** Bị vô hiệu hoá (nghỉ việc, tự đóng tài khoản...) — chặn vĩnh viễn cho tới khi admin mở lại. */
    DISABLED,

    /** Khoá tạm. Hết hạn {@code lockedUntil} là tự mở; {@code lockedUntil} null nghĩa là khoá vô thời hạn. */
    LOCKED
}
