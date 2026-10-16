package com.structure.springboot.features.account.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Người dùng tự đăng ký qua {@code POST /auth/register}.
 *
 * <p>Khác {@code AccountRequest} ở hai điểm sống còn: KHÔNG nhận {@code roleId} (tự đăng ký mà
 * chọn được vai trò thì ai cũng tự phong mình làm admin), và có thêm {@code fullName} + {@code phone}
 * của hồ sơ.</p>
 *
 * <p>Gói dữ liệu của hai bảng trong một request là cố ý — cùng tiền lệ với {@code OrderRequest}
 * (đơn hàng + dòng hàng). Người dùng điền một form thì backend phải ghi một lần, nếu tách hai
 * lượt gọi mà lượt sau hỏng là sinh ra tài khoản cụt hồ sơ.</p>
 *
 * <p>Đặt ở module {@code account} chứ không phải {@code auth}: account mới là nơi sở hữu dữ liệu,
 * để bên kia thì {@code AccountServiceImpl} phải import ngược vào {@code auth}.</p>
 */
public record AccountRegistrationRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter and one digit")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String fullName,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^0\\d{9}$", message = "Phone must be 10 digits and start with 0")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String phone
) {
}
