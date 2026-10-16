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
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
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
    private final AuthCookieProperties cookieProperties;
    private final AuthCookieFactory authCookieFactory;

    // Endpoint công khai của module auth (login/refresh/logout) phải được bỏ qua filter này.
    // Lý do: cookie access_token có path "/" nên trình duyệt gửi kèm MỌI request; nếu token đã hết hạn
    // thì filter sẽ ném 401 ngay tại /auth/login khiến người dùng không thể đăng nhập lại.
    private static final String AUTH_PATH_PREFIX = "/auth/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = request.getRequestURI().substring(contextPath != null ? contextPath.length() : 0);
        return path.startsWith(AUTH_PATH_PREFIX);
    }

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
                // Bỏ vế "typ != null": token thiếu claim này từng được thả cho qua, tức là chốt
                // chặn sẽ im lặng mở ra nếu có ngày phát sinh một nhánh phát token quên gắn typ.
                if (!"access".equals(claims.get("typ", String.class))) {
                    throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid token type: expected access token");
                }

                String email = claims.getSubject();

                // Thiếu "sub" thì không tra được mốc thu hồi, mà bỏ qua khâu tra đó nghĩa là
                // token vẫn sống sau khi người dùng đã bấm logout-all. Từ chối thẳng.
                if (email == null) {
                    throw new ApiException(ErrorCode.UNAUTHORIZED, "Token is missing the subject claim");
                }

                // 3. Kiểm tra token đã bị thu hồi chưa (logout phiên / logout-all / đổi mật khẩu).
                //    Gộp cả hai điều kiện vào một lần gọi Redis để không làm chậm mọi request.
                long issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0L;
                if (refreshTokenRedis.isAccessTokenRevoked(email, claims.get("sid", String.class), issuedAt)) {
                    throw new ApiException(ErrorCode.UNAUTHORIZED, "Session has been revoked. Please sign in again.");
                }

                // 4. Bóc tách Role và UID trực tiếp từ Token (Không query DB)
                String scope = claims.get("scope", String.class);

                List<SimpleGrantedAuthority> authorities = List.of();
                if (StringUtils.hasText(scope)) {
                    authorities = Arrays.stream(scope.split(" "))
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                }

                // 5. Nạp danh tính vào SecurityContext.
                //    Principal là AuthenticatedUser (mang cả id lẫn email) thay vì chuỗi email trần,
                //    nhờ vậy controller lấy được id người đăng nhập qua @AuthenticationPrincipal
                //    mà không phải bóc token lần nữa. getName() vẫn trả về email như cũ.
                AuthenticatedUser principal = AuthenticatedUser.of(claims.get("uid", String.class), email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                //    details để đúng thứ Spring quy ước: IP + session id của request, phục vụ audit.
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

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
                if (cookieProperties.accessTokenName().equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void clearAuthCookies(HttpServletResponse response) {
        // Dùng chung AuthCookieFactory với luồng login/logout -> cookie xoá luôn khớp
        // chính xác tên, path, Secure, SameSite của cookie đã ghi ra.
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.clearAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.clearRefreshTokenCookie().toString());
    }
}
