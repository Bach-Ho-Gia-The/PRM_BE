package com.structure.springboot.features.category.mapper;

import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.response.CategoryPublicResponse;
import com.structure.springboot.features.category.dto.response.CategoryResponse;
import com.structure.springboot.features.category.entity.CategoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    CategoryResponse toResponse(CategoryEntity category);

    CategoryPublicResponse toPublicResponse(CategoryEntity category);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CategoryEntity toEntity(CategoryRequest request);
}
