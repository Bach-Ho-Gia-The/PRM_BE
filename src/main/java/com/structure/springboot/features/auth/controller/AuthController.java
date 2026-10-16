package com.structure.springboot.features.auth.controller;

import com.structure.springboot.common.response.ApiResponse;
import com.structure.springboot.features.account.dto.request.AccountRegistrationRequest;
import com.structure.springboot.common.security.AuthCookieFactory;
import com.structure.springboot.features.auth.dto.request.ForgotPasswordRequest;
import com.structure.springboot.features.auth.dto.request.GoogleLoginRequest;
import com.structure.springboot.features.auth.dto.request.LoginRequest;
import com.structure.springboot.features.auth.dto.request.ResetPasswordRequest;
import com.structure.springboot.features.auth.dto.request.TokenRequest;
import com.structure.springboot.features.auth.dto.request.VerifyOtpRequest;
import com.structure.springboot.features.auth.dto.request.VerifyRegistrationRequest;
import com.structure.springboot.features.auth.dto.response.LoginResponse;
import com.structure.springboot.features.auth.dto.response.OtpTicketResponse;
import com.structure.springboot.features.auth.dto.response.RegistrationPendingResponse;
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
     * Bước 1 của đăng ký: nhận hồ sơ, gửi mã 6 số, <b>chưa tạo tài khoản nào</b>.
     *
     * <p>Trả 200 kèm {@link RegistrationPendingResponse} — KHÔNG có token, vì chưa có tài khoản
     * để phát. Client phải giữ {@code registrationId} rồi gọi {@code /auth/verify-registration}.</p>
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationPendingResponse>> register(
            @Valid @RequestBody AccountRegistrationRequest requestDTO,
            HttpServletRequest request) {

        // KHÔNG set cookie và KHÔNG đọc X-Client-Type ở đây: chưa có tài khoản thì chưa có token
        // nào để phát. Hai thứ đó chuyển hết sang /auth/verify-registration.
        return ResponseEntity.ok(
                ApiResponse.<RegistrationPendingResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("A verification code has been sent to your email")
                        .result(authService.register(requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Bước 2 của đăng ký: nhập đúng mã thì tài khoản được tạo và người dùng vào thẳng app.
     *
     * <p>Đây mới là nơi trả 201 và phát token — tôn trọng {@code X-Client-Type} y hệt
     * {@code /auth/login} nên client tái dùng nguyên code xử lý sau đăng nhập.</p>
     */
    @PostMapping("/verify-registration")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyRegistration(
            @Valid @RequestBody VerifyRegistrationRequest requestDTO,
            @RequestHeader(value = "X-Client-Type", required = false, defaultValue = "web")
            String clientType,
            HttpServletRequest request) {

        return sessionResponse(authService.verifyRegistration(requestDTO),
                clientType, HttpStatus.CREATED, "Registration completed", request);
    }

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
        return sessionResponse(authService.login(requestDTO, request.getRemoteAddr()),
                clientType, HttpStatus.OK, "Login successful", request);
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

        return sessionResponse(result, clientType, HttpStatus.OK, "Token refreshed successfully", request);
    }

    /**
     * Đăng xuất <b>thiết bị hiện tại</b>. Các thiết bị khác của cùng tài khoản vẫn đăng nhập.
     *
     * <p>Token <b>hết hạn vẫn đăng xuất được</b> ({@code parseAllowingExpired}): chữ ký đã được
     * xác minh xong trước khi JJWT kiểm hạn, nên claims bên trong vẫn đáng tin.</p>
     *
     * <p>Nhưng <b>không đọc nổi nguồn token nào thì trả 401</b>, không trả 200 cho xong — xem lý
     * do ở {@code AuthServiceImpl.firstRevocableClaims}. Client nhận 401 ở đây vẫn phải xoá sạch
     * token đã lưu phía mình, vì 401 nghĩa là không còn gì để thu hồi.</p>
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
     * Đăng nhập bằng Google.
     *
     * <p>Frontend dùng Google Identity Services để lấy {@code idToken} rồi gửi về đây; backend
     * xác minh chữ ký với Google rồi phát token của chính hệ thống. Người chưa có tài khoản sẽ
     * được tạo ngay trong lần gọi này.</p>
     *
     * <p>Tôn trọng {@code X-Client-Type} y hệt {@code /auth/login} nên client không phải viết
     * thêm nhánh xử lý nào.</p>
     */
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest requestDTO,
            @RequestHeader(value = "X-Client-Type", required = false, defaultValue = "web")
            String clientType,
            HttpServletRequest request) {

        return sessionResponse(authService.loginWithGoogle(requestDTO),
                clientType, HttpStatus.OK, "Login successful", request);
    }

    /**
     * Bước 1 quên mật khẩu — <b>luôn trả 200</b>.
     *
     * <p>Câu trả lời giống hệt nhau dù email có tồn tại hay không. Phân biệt hai trường hợp là
     * biến endpoint công khai này thành công cụ kiểm tra "email X đã đăng ký chưa", đúng kiểu rò
     * rỉ mà {@code /auth/login} đã chặn bằng một câu lỗi chung.</p>
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest requestDTO,
            HttpServletRequest request) {

        authService.forgotPassword(requestDTO);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("If that email is registered, a verification code has been sent")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /** Bước 2: đổi mã 6 số lấy vé tạm dùng cho bước đặt lại mật khẩu. */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<OtpTicketResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest requestDTO,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<OtpTicketResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Verification successful")
                        .result(authService.verifyOtp(requestDTO))
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Bước 3: đặt mật khẩu mới bằng vé tạm.
     *
     * <p>Xoá cookie trong phản hồi vì mật khẩu mới đã kéo theo thu hồi toàn bộ phiên — để lại
     * cookie chết trong trình duyệt thì lần gọi kế tiếp chỉ nhận 401 mà không hiểu vì sao.</p>
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest requestDTO,
            HttpServletRequest request) {

        authService.resetPassword(requestDTO);

        return clearCookies(ResponseEntity.status(HttpStatus.OK)).body(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Password has been reset. Please sign in again.")
                        .path(request.getRequestURI())
                        .build()
        );
    }

    /**
     * Dựng phản hồi cho mọi endpoint <b>phát ra một phiên đăng nhập</b>: login, refresh, google,
     * verify-registration. Trước đó bốn chỗ chép y hệt mười dòng này — đổi chính sách cookie mà
     * quên một chỗ là sinh ra lỗ hổng rất khó nhìn ra.
     *
     * <p>{@code X-Client-Type: mobile} thì token nằm trong body cho app tự cất; còn lại token đi
     * vào HttpOnly cookie và bị gỡ khỏi body.</p>
     */
    private ResponseEntity<ApiResponse<LoginResponse>> sessionResponse(
            LoginResponse result, String clientType, HttpStatus status,
            String message, HttpServletRequest request) {

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status);

        if (!"mobile".equalsIgnoreCase(clientType)) {
            responseBuilder
                    .header(HttpHeaders.SET_COOKIE, authCookieFactory.accessTokenCookie(result.accessToken()).toString())
                    .header(HttpHeaders.SET_COOKIE, authCookieFactory.refreshTokenCookie(result.refreshToken()).toString());

            result = hideTokens(result);
        }

        return responseBuilder.body(
                ApiResponse.<LoginResponse>builder()
                        .code(status.value())
                        .message(message)
                        .result(result)
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
                result.userId(), result.email(), result.roleName());
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
