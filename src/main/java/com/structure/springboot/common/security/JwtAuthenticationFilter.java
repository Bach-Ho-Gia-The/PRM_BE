package com.structure.springboot.common.security;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RefreshTokenRedis refreshTokenRedis;
    private final HandlerExceptionResolver handlerExceptionResolver;

    // Đọc động từ application.yaml -> theo sát biến môi trường .env
    @Value("${app.auth.cookie.access-token-name:access_token}")
    private String accessTokenName;

    @Value("${app.auth.cookie.refresh-token-name:refresh_token}")
    private String refreshTokenName;

    @Value("${app.auth.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.auth.cookie.path:/}")
    private String cookiePath;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Trích xuất token từ Header hoặc Cookie
        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parseClaims(token);

                // 2. Kiểm tra bắt buộc phải là ACCESS token
                String typ = claims.get("typ", String.class);
                if (typ != null && !"access".equals(typ)) {
                    throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid token type: expected access token");
                }

                String email = claims.getSubject();

                // 3. Kiểm tra token có bị thu hồi do logout / đổi mật khẩu chưa
                if (email != null) {
                    Long logoutTime = refreshTokenRedis.getLogoutTime(email);
                    if (logoutTime != null && claims.getIssuedAt() != null) {
                        if (claims.getIssuedAt().getTime() < logoutTime) {
                            throw new ApiException(ErrorCode.UNAUTHORIZED, "Token has been revoked due to logout or password reset.");
                        }
                    }
                }

                // 4. Bóc tách Role và UID trực tiếp từ Token (Không query DB)
                String scope = claims.get("scope", String.class);
                String uid = claims.get("uid", String.class);

                List<SimpleGrantedAuthority> authorities = List.of();
                if (StringUtils.hasText(scope)) {
                    authorities = Arrays.stream(scope.split(" "))
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                }

                // 5. Nạp danh tính vào SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);
                authentication.setDetails(uid);

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                log.warn("JWT authentication failed: {}", e.getMessage());

                // Xóa Cookie tự động theo cấu hình môi trường hiện tại
                clearAuthCookies(response);

                handlerExceptionResolver.resolveException(request, response, null, e);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (accessTokenName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void clearAuthCookies(HttpServletResponse response) {
        // Tự động ăn theo biến môi trường: Local là false/Lax, Deploy là true/None
        ResponseCookie clearAccess = ResponseCookie.from(accessTokenName, "")
                .path(cookiePath)
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .build();

        ResponseCookie clearRefresh = ResponseCookie.from(refreshTokenName, "")
                .path(cookiePath) // Refresh token thường giới hạn path ở endpoint auth
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());
    }
}