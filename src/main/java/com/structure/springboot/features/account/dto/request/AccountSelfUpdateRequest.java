package com.structure.springboot.features.account.dto.request;

import com.structure.springboot.common.config.JacksonConfig;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Người dùng tự sửa phần định danh của CHÍNH MÌNH.
 *
 * <p><b>Vì sao không dùng lại {@code AccountUpdateRequest}:</b> DTO đó có {@code roleId}. Dùng
 * nó cho {@code /accounts/me} thì bất kỳ ai cũng tự nâng mình lên ADMIN bằng một lời gọi API.
 * Bỏ trường nguy hiểm ra khỏi DTO là cách chặn chắc chắn nhất — không có chỗ nào để điền thì
 * không có gì để quên kiểm tra.</p>
 *
 * <p>Tên thật, số điện thoại, ngày sinh… <b>không</b> ở đây mà thuộc {@code PATCH /profiles/me}.</p>
 */
public record AccountSelfUpdateRequest(

        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @JsonDeserialize(using = JacksonConfig.StringTrimDeserializer.class)
        String email
) {
}
