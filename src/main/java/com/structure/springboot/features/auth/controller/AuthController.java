package com.structure.springboot.features.auth.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.common.security.AuthCookieFactory;
import com.structure.springboot.features.auth.dto.request.LoginRequest;
import com.structure.springboot.features.auth.dto.request.TokenRequest;
import com.structure.springboot.features.auth.dto.response.LoginResponse;
import com.structure.springboot.features.auth.security.TokenSourceResolver;
import com.structure.springboot.features.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory authCookieFactory;
    private final TokenSourceResolver tokenSourceResolver;


    /**
     * Đăng nhập hỗ trợ đồng thời hai kiểu client:
     * <ul>
     *   <li><b>Mặc định / {@code X-Client-Type: web}</b> — token được set vào HttpOnly cookie
     *       ({@code access_token}, {@code refresh_token}) và KHÔNG xuất hiện trong body.</li>
     *   <li><b>{@code X-Client-Type: mobile}</b> — không set cookie, token trả thẳng trong body
     *       để app tự lưu secure storage và gắn header {@code Authorization: Bearer ...}.</li>
     * </ul>
     * Hai kiểu dùng chung một luồng nghiệp vụ; {@code JwtAuthenticationFilter} vốn đã đọc được
     * cả header lẫn cookie nên các API phía sau không cần biết client thuộc loại nào.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest requestDTO,
            @RequestHeader(value = "X-Client-Type", required = false, defaultValue = "web")
            String clientType,
            HttpServletRequest request) {

        // getRemoteAddr() đã trả về IP thật của client nhờ server.forward-headers-strategy=framework
        // trong application.yaml — Spring tự đọc X-Forwarded-For do proxy/load balancer gắn vào.
        LoginResponse result = authService.login(requestDTO, request.getRemoteAddr());
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.OK);

        if (!"mobile".equalsIgnoreCase(clientType)) {
            responseBuilder
                    .header(HttpHeaders.SET_COOKIE, authCookieFactory.accessTokenCookie(result.accessToken()).toString())
                    .header(HttpHeaders.SET_COOKIE, authCookieFactory.refreshTokenCookie(result.refreshToken()).toString());

            result = hideTokens(result);
        }

        return responseBuilder.body(
                ApiResponse.<LoginResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Login successful")
                        .result(result)
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Cấp lại cặp token mới từ refresh token còn hạn, người dùng không phải đăng nhập lại.
     *
     * <p>Trả về đúng cấu trúc của {@code /auth/login} và tôn trọng cùng một header
     * {@code X-Client-Type}, nên phía client chỉ cần tái dùng y nguyên code xử lý login.</p>
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @RequestBody(required = false) TokenRequest requestDTO,
            @RequestHeader(value = "X-Client-Type", required = false, defaultValue = "web")
            String clientType,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse result = withCookieCleanupOnFailure(response,
                () -> authService.refresh(tokenSourceResolver.collect(requestDTO, request)));
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.OK);

        if (!"mobile".equalsIgnoreCase(clientType)) {
            responseBuilder
                    .header(HttpHeaders.SET_COOKIE, authCookieFactory.accessTokenCookie(result.accessToken()).toString())
                    .header(HttpHeaders.SET_COOKIE, authCookieFactory.refreshTokenCookie(result.refreshToken()).toString());

            result = hideTokens(result);
        }

        return responseBuilder.body(
                ApiResponse.<LoginResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Token refreshed successfully")
                        .result(result)
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Đăng xuất <b>thiết bị hiện tại</b>. Các thiết bị khác của cùng tài khoản vẫn đăng nhập.
     *
     * <p>Luôn trả 200 kể cả khi không tìm thấy token hay token đã hết hạn: đăng xuất là thao tác
     * idempotent, báo lỗi chỉ khiến client mắc kẹt mà chẳng tăng được chút an toàn nào.</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) TokenRequest requestDTO,
            HttpServletRequest request,
            HttpServletResponse response) {

        withCookieCleanupOnFailure(response, () -> {
            authService.logout(tokenSourceResolver.collect(requestDTO, request));
            return null;
        });
        return clearCookies(ResponseEntity.status(HttpStatus.OK)).body(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Logout successful")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Đăng xuất <b>toàn bộ thiết bị</b> của tài khoản. Dùng khi nghi ngờ lộ tài khoản,
     * và là cùng một cơ chế mà đổi mật khẩu / khoá tài khoản sẽ gọi tới.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @RequestBody(required = false) TokenRequest requestDTO,
            HttpServletRequest request,
            HttpServletResponse response) {

        withCookieCleanupOnFailure(response, () -> {
            authService.logoutAll(tokenSourceResolver.collect(requestDTO, request));
            return null;
        });
        return clearCookies(ResponseEntity.status(HttpStatus.OK)).body(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Logged out from all devices")
                        .path(request.getRequestURI())
                        .build()
        );
    }


    /**
     * Token đã nằm trong HttpOnly cookie thì phải biến mất khỏi body, nếu không JavaScript đọc được
     * và cái HttpOnly kia thành vô nghĩa. Record là immutable nên dựng bản sao thay vì set null.
     */
    private LoginResponse hideTokens(LoginResponse result) {
        return new LoginResponse(null, null, result.tokenType(), result.expiresIn(),
                result.userId(), result.username(), result.email(), result.roleName());
    }

    /**
     * Chạy một thao tác auth, và nếu nó ném lỗi thì <b>xoá cookie trước khi để lỗi bay lên</b>.
     *
     * <p>Vì sao cần: {@code JwtAuthenticationFilter.shouldNotFilter()} bỏ qua toàn bộ {@code /auth/**},
     * nên nhánh dọn cookie trong filter không bao giờ chạy cho các endpoint ở đây. Hậu quả là
     * refresh token chết vẫn nằm lì trong trình duyệt suốt 7 ngày và lần nào gọi cũng 401.</p>
     *
     * <p>Header gắn thẳng lên {@code HttpServletResponse} nên vẫn còn nguyên khi
     * {@code GlobalExceptionHandler} ghi body — đúng cách {@code JwtAuthenticationFilter} đang làm,
     * không đẻ thêm pattern mới.</p>
     */
    private <T> T withCookieCleanupOnFailure(HttpServletResponse response, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException ex) {
            response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.clearAccessTokenCookie().toString());
            response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.clearRefreshTokenCookie().toString());
            throw ex;
        }
    }


    /** Ghi đè hai cookie bằng maxAge = 0 để trình duyệt xoá ngay, kể cả khi server không thu hồi được gì. */
    private ResponseEntity.BodyBuilder clearCookies(ResponseEntity.BodyBuilder builder) {
        return builder
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.clearRefreshTokenCookie().toString());
    }
}
