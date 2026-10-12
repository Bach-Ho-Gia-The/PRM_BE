package com.structure.springboot.features.account.mapper;

import com.structure.springboot.features.account.dto.request.AccountRequest;
import com.structure.springboot.features.account.dto.request.AccountUpdateRequest;
import com.structure.springboot.features.account.dto.response.AccountResponse;
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
    AccountResponse toResponse(AccountEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AccountEntity toEntity(AccountRequest request);

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
