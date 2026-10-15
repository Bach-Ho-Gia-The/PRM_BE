package com.structure.springboot.features.role.mapper;

import com.structure.springboot.features.role.dto.request.RoleRequest;
import com.structure.springboot.features.role.dto.request.RoleUpdateRequest;
import com.structure.springboot.features.role.dto.response.RoleResponse;
import com.structure.springboot.features.role.entity.RoleEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleMapper {

    RoleResponse toResponse(RoleEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RoleEntity toEntity(RoleRequest request);

    /**
     * MapStruct bỏ qua mọi field null của request ({@code NullValuePropertyMappingStrategy.IGNORE})
     * ⇒ PATCH chỉ ghi đè đúng những field client thực sự gửi lên.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(RoleUpdateRequest request, @MappingTarget RoleEntity entity);
}
