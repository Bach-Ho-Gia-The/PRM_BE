package com.structure.springboot.features.payment.mapper;

import com.structure.springboot.features.payment.dto.response.PaymentResponse;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    PaymentResponse toResponse(PaymentEntity entity);
}
