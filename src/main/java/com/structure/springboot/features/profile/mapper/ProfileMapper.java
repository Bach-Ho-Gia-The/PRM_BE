package com.structure.springboot.features.profile.mapper;

import com.structure.springboot.features.profile.dto.request.ProfileUpdateRequest;
import com.structure.springboot.features.profile.dto.response.ProfileResponse;
import com.structure.springboot.features.profile.entity.ProfileEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProfileMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "email", source = "account.email")
    /*
     * Hồ sơ coi là xong khi có đủ tên và số điện thoại — hai thứ tối thiểu để gọi tên khách và
     * liên lạc giao hàng. Backend tự trả lời thay vì để hai frontend (web + mobile) mỗi bên tự
     * suy luận từ dữ liệu rồi lệch nhau.
     *
     * Dùng expression chứ không @AfterMapping: ProfileResponse là record bất biến, MapStruct
     * dựng nó bằng constructor nên không có @MappingTarget để gắn hàm hậu xử lý vào.
     * Hai field đọc ở đây đều là cột thường của chính ProfileEntity, không chạm quan hệ lazy.
     */
    @Mapping(target = "profileCompleted",
            expression = "java(entity.getFullName() != null && entity.getPhone() != null)")
    ProfileResponse toResponse(ProfileEntity entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "avatarPublicId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ProfileUpdateRequest request, @MappingTarget ProfileEntity entity);
}
