package com.structure.springboot.features.notification.mapper;

import com.structure.springboot.features.notification.dto.response.NotificationResponse;
import com.structure.springboot.features.notification.entity.NotificationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    /**
     * {@code isRead} phải map tường minh: Lombok sinh getter {@code isRead()} cho field
     * {@code isRead}, nên theo quy ước JavaBeans property có tên là {@code read}. Để MapStruct tự
     * đoán thì nó không tìm thấy nguồn và âm thầm trả về {@code false} ở mọi thông báo.
     */
    @Mapping(target = "isRead", source = "read")
    NotificationResponse toResponse(NotificationEntity notification);
}
