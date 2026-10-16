package com.structure.springboot.common.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import com.structure.springboot.common.security.AuthCookieProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${spring.application.name:Application}") String appName,
            @Value("${app.api.version:1.0.0}") String appVersion,
            @Value("${app.api.description:API Documentation}") String appDescription,
            AuthCookieProperties cookieProperties) {

        final String bearerAuthScheme = "bearerAuth";
        final String cookieAuthScheme = "cookieAuth";

        return new OpenAPI()
                .info(new Info()
                        .title(appName.toUpperCase() + " SERVICE APIS")
                        .version(appVersion)
                        .description(appDescription))
                // Không set servers cứng để Swagger tự lấy Host hiện tại của trình duyệt
                .addSecurityItem(new SecurityRequirement()
                        .addList(bearerAuthScheme)
                        .addList(cookieAuthScheme))
                .components(new Components()
                        .addSecuritySchemes(bearerAuthScheme, new SecurityScheme()
                                .name(bearerAuthScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Authorization header using the Bearer scheme."))
                        .addSecuritySchemes(cookieAuthScheme, new SecurityScheme()
                                // Đọc từ app.auth.cookie.* thay vì viết cứng: đổi tên cookie trong
                                // .env mà Swagger vẫn chỉ đúng chỗ, khỏi "Try it out" mãi không chạy.
                                .name(cookieProperties.accessTokenName())
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .description("JWT via " + cookieProperties.accessTokenName() + " cookie.")));
    }
}
