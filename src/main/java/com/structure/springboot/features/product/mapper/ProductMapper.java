package com.structure.springboot.features.product.mapper;

import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.dto.response.ProductImageResponse;
import com.structure.springboot.features.product.dto.response.ProductResponse;
import com.structure.springboot.features.product.dto.response.ProductVariantResponse;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductImageEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.categoryName")
    @Mapping(target = "categorySlug", source = "category.slug")
    ProductResponse toResponse(ProductEntity entity);

    /**
     * MapStruct tự dùng hai method bên dưới để map {@code images -> gallery} và
     * {@code variants -> variants}, nên không phải viết tay vòng lặp nào.
     */
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.categoryName")
    @Mapping(target = "categorySlug", source = "category.slug")
    @Mapping(target = "gallery", source = "images")
    ProductDetailResponse toDetailResponse(ProductEntity entity);

    ProductVariantResponse toVariantResponse(ProductVariantEntity entity);

    ProductImageResponse toImageResponse(ProductImageEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "thumbnailPublicId", ignore = true)
    @Mapping(target = "variants", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductEntity toEntity(ProductRequest request);

    /**
     * MapStruct bỏ qua mọi field null của request ({@code NullValuePropertyMappingStrategy.IGNORE})
     * ⇒ PATCH chỉ ghi đè đúng những field client thực sự gửi lên.
     *
     * <p>{@code slug} bị ignore ở đây vì nó phải đi qua bước kiểm tra trùng và chuẩn hoá trong
     * service, không được để mapper gán thẳng chuỗi thô của client vào entity.</p>
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "thumbnailPublicId", ignore = true)
    @Mapping(target = "variants", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ProductUpdateRequest request, @MappingTarget ProductEntity entity);
}
