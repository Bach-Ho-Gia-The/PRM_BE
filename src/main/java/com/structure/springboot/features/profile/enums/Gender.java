package com.structure.springboot.features.profile.enums;

/**
 * Giới tính hiển thị trên hồ sơ.
 *
 * <p>Có {@code OTHER} để người dùng không bị ép chọn sai; frontend tự dịch sang tiếng Việt,
 * backend chỉ lưu đúng ba giá trị này.</p>
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER
}
