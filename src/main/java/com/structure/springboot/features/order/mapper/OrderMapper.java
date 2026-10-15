package com.structure.springboot.features.order.mapper;

import com.structure.springboot.features.order.dto.response.OrderItemResponse;
import com.structure.springboot.features.order.dto.response.OrderResponse;
import com.structure.springboot.features.order.dto.response.OrderSummaryResponse;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.entity.OrderItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "accountId", source = "account.id")
    OrderResponse toResponse(OrderEntity entity);

    @Mapping(target = "productId", source = "product.id")
    OrderItemResponse toItemResponse(OrderItemEntity entity);

    /** Đọc thẳng cột {@code totalItems} — KHÔNG được cộng từ {@code items} vì quan hệ đó lazy. */
    OrderSummaryResponse toSummaryResponse(OrderEntity entity);
}
