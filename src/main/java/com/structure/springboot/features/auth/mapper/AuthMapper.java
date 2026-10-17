package com.structure.springboot.features.auth.mapper;

import com.structure.springboot.features.auth.dto.response.LoginResponse;
import com.structure.springboot.features.auth.dto.response.OtpTicketResponse;
import com.structure.springboot.features.auth.dto.response.RegistrationPendingResponse;
import com.structure.springboot.features.auth.security.AccountPrincipal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthMapper {

    /** Hai tham số vô hướng ghép thành record — MapStruct khớp theo TÊN tham số nên không cần
     *  {@code @Mapping}. Cùng kiểu với cách {@code toLoginResponse} nhận accessToken/expiresIn. */
    OtpTicketResponse toOtpTicketResponse(String ticket, long expiresIn);

    RegistrationPendingResponse toRegistrationPendingResponse(String registrationId, String email,
                                                               long expiresIn, long resendAfter);

    /*
     * Bốn tham số token KHÔNG cần @Mapping: MapStruct tự khớp property của target với TÊN tham số
     * khi không nhập nhằng. Khai báo thêm chỉ sinh ra đúng cùng một đoạn code, tốn 4 dòng phải đọc.
     * Chỉ còn lại các @Mapping thật sự cần: bóc field từ bên trong object principal.
     */
    @Mapping(target = "userId", source = "principal.id")
    @Mapping(target = "email", source = "principal.email")
    @Mapping(target = "roleName", source = "principal.roleName")
    LoginResponse toLoginResponse(AccountPrincipal principal,
                                  String accessToken,
                                  String refreshToken,
                                  String tokenType,
                                  Long expiresIn);
}
