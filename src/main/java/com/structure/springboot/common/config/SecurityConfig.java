package com.structure.springboot.common.config;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.AuthCookieProperties;
import com.structure.springboot.common.security.JwtAuthenticationFilter;
import com.structure.springboot.common.security.JwtProperties;
import com.structure.springboot.common.security.LoginAttemptProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, AuthCookieProperties.class, LoginAttemptProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter headerAuthFilter;
    private final HandlerExceptionResolver handlerExceptionResolver;

    // Các endpoint cố định của hệ thống
    private static final String[] SYSTEM_WHITELIST = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**",
            "/actuator/health"
    };

    // Đọc whitelist nghiệp vụ từ application.yaml
    @Value("${app.security.whitelist:}")
    private String[] businessWhitelist;

    // Tiền tố context-path (vd /api/v1). Spring Security 7 (Boot 4) so khớp request theo đường dẫn
    // CÒN NGUYÊN context-path, nên whitelist viết "/products" sẽ không khớp "/api/v1/products" ⇒ 401.
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * Nhân đôi mỗi pattern thành 2 biến thể: dạng gốc ("/products") và dạng có tiền tố
     * context-path ("/api/v1/products"). Nhờ vậy whitelist khớp bất kể Spring so khớp theo
     * đường dẫn đã cắt hay chưa cắt context-path.
     */
    private String[] expandWithContextPath(String[] patterns) {
        if (contextPath == null || contextPath.isBlank()) {
            return patterns;
        }
        java.util.List<String> all = new java.util.ArrayList<>();
        for (String p : patterns) {
            all.add(p);
            all.add(contextPath + p);
        }
        return all.toArray(new String[0]);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults()) // Kích hoạt CORS
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(expandWithContextPath(SYSTEM_WHITELIST)).permitAll()
                        .requestMatchers(expandWithContextPath(businessWhitelist)).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        // Xử lý 401 Unauthorized
                        .authenticationEntryPoint((request, response, authEx) ->
                                handlerExceptionResolver.resolveException(request, response, null,
                                        new ApiException(ErrorCode.UNAUTHORIZED)))
                        // Xử lý 403 Forbidden
                        .accessDeniedHandler((request, response, accessDeniedEx) ->
                                handlerExceptionResolver.resolveException(request, response, null,
                                        new ApiException(ErrorCode.FORBIDDEN)))
                )
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-Client-Type: cho phép client khai báo là "web" (nhận token qua cookie) hay "mobile" (nhận token trong body)
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "X-Client-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
