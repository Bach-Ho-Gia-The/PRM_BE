package com.structure.springboot.features.store.mapper;

import com.structure.springboot.features.store.dto.request.StoreRequest;
import com.structure.springboot.features.store.dto.request.StoreUpdateRequest;
import com.structure.springboot.features.store.dto.response.StoreResponse;
import com.structure.springboot.features.store.entity.StoreEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StoreMapper {

    StoreResponse toResponse(StoreEntity store);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    StoreEntity toEntity(StoreRequest request);

    /** PATCH: field null trong request không được ghi đè giá trị đang có. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(StoreUpdateRequest request, @MappingTarget StoreEntity store);
}
