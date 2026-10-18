package com.structure.springboot.features.account.mapper;

import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.request.AccountUpdateRequest;
import com.structure.springboot.features.account.dto.response.AccountResponse;
import com.structure.springboot.features.account.dto.response.EmailChangePendingResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountMapper {

    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "roleName", source = "role.roleName")
    // Chỉ báo CÓ hay KHÔNG, tuyệt đối không lộ chuỗi băm ra ngoài. Frontend cần cờ này để biết
    // nên hiện form "đổi mật khẩu" hay form "đặt mật khẩu lần đầu" cho tài khoản Google.
    @Mapping(target = "hasPassword", expression = "java(entity.getPassword() != null)")
    AccountResponse toResponse(AccountEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AccountEntity toEntity(AccountRequest request);

    /** Ba tham số vô hướng ghép thành record — MapStruct khớp theo TÊN tham số, không cần @Mapping. */
    EmailChangePendingResponse toEmailChangePendingResponse(String newEmail, long expiresIn, long resendAfter);

    /**
     * MapStruct bỏ qua mọi field null của request ({@code NullValuePropertyMappingStrategy.IGNORE})
     * ⇒ PATCH chỉ ghi đè đúng những field client thực sự gửi lên.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(AccountUpdateRequest request, @MappingTarget AccountEntity entity);
}
