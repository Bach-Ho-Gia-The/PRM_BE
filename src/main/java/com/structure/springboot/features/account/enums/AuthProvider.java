package com.structure.springboot.features.account.enums;

/**
 * Nguồn gốc của một tài khoản.
 *
 * <p>{@code LOCAL} — tự đăng ký bằng email + mật khẩu. {@code GOOGLE} — tạo ra từ lần đăng nhập
 * Google đầu tiên, lúc đó chưa có mật khẩu.</p>
 *
 * <p>Giá trị này <b>không đổi sau khi tạo</b>. Một tài khoản LOCAL liên kết thêm Google thì chỉ
 * được điền {@code providerId}, vẫn giữ {@code provider = LOCAL} vì mật khẩu của họ còn dùng được.</p>
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
