package com.structure.springboot.features.auth.enums;

/**
 * Việc mà một mã OTP đang phục vụ. Giá trị này đi vào tên khoá Redis nên hai luồng không bao giờ
 * giẫm lên mã của nhau, và lúc soi {@code redis-cli --scan} thì nhìn khoá là biết nó của ai.
 */
public enum OtpPurpose {
    /** Khôi phục mật khẩu cho tài khoản đã tồn tại. */
    RESET,
    /** Xác minh email trước khi tài khoản được tạo. */
    REGISTER,
    /** Xác minh ĐỊA CHỈ MỚI trước khi dời email của một tài khoản đang dùng. */
    CHANGE_EMAIL
}
