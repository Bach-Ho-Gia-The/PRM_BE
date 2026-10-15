package com.structure.springboot.features.profile.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import com.structure.springboot.features.profile.enums.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;

/**
 * Cập nhật hồ sơ theo kiểu PATCH: field nào không gửi (null) là giữ nguyên.
 *
 * <p>Không field nào bắt buộc — người dùng điền tới đâu lưu tới đó. Hệ quả cần biết:
 * mapper dùng {@code NullValuePropertyMappingStrategy.IGNORE} nên <b>không xoá trắng một
 * field bằng cách gửi null</b>; muốn xoá thì gửi chuỗi rỗng.</p>
 */
public record ProfileUpdateRequest(

        @Size(max = 150, message = "Full name must not exceed 150 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String fullName,

        @Pattern(regexp = "^$|^0\\d{8,10}$", message = "Phone must start with 0 and contain 9 to 11 digits")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String phone,

        // Chặn ngày sinh ở tương lai. Không chặn mốc quá khứ xa: dữ liệu nhập sai kiểu 1900
        // là chuyện của báo cáo, không đáng để API từ chối.
        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        Gender gender,

        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String avatarUrl,

        @Size(max = 500, message = "Address must not exceed 500 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String address
) {
}
