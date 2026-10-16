package com.structure.springboot.features.collection.mapper;

import com.structure.springboot.features.collection.dto.request.CollectionCreateRequest;
import com.structure.springboot.features.collection.dto.response.CollectionProductResponse;
import com.structure.springboot.features.collection.dto.response.CollectionPublicProductResponse;
import com.structure.springboot.features.collection.dto.response.CollectionPublicSummaryResponse;
import com.structure.springboot.features.collection.dto.response.CollectionSummaryResponse;
import com.structure.springboot.features.collection.entity.CollectionEntity;
import com.structure.springboot.features.collection.entity.CollectionProductEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CollectionMapper {

    CollectionSummaryResponse toSummaryResponse(CollectionEntity entity);

    CollectionPublicSummaryResponse toPublicSummaryResponse(CollectionEntity entity);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "productSlug", source = "product.slug")
    @Mapping(target = "thumbnail", source = "product.thumbnail")
    @Mapping(target = "status", source = "product.status")
    CollectionProductResponse toProductResponse(CollectionProductEntity entity);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "productSlug", source = "product.slug")
    @Mapping(target = "thumbnail", source = "product.thumbnail")
    @Mapping(target = "price", source = "product.price")
    @Mapping(target = "originalPrice", source = "product.originalPrice")
    @Mapping(target = "tag", source = "product.tag")
    CollectionPublicProductResponse toPublicProductResponse(CollectionProductEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CollectionEntity toEntity(CollectionCreateRequest request);
}
