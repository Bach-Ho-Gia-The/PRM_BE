package com.structure.springboot.features.auth.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.JwtProperties;
import com.structure.springboot.common.security.JwtService;
import com.structure.springboot.common.security.RefreshTokenRedis;
import com.structure.springboot.features.account.dto.request.AccountRegistrationRequest;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.service.AccountService;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.auth.dto.request.LoginRequest;
import com.structure.springboot.features.auth.dto.response.LoginResponse;
import com.structure.springboot.features.auth.mapper.AuthMapper;
import com.structure.springboot.features.auth.security.AccountPrincipal;
import com.structure.springboot.features.auth.security.LoginAttemptGuard;
import com.structure.springboot.features.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRedis refreshTokenRedis;
    private final AuthMapper authMapper;
    private final AccountRepository accountRepository;
    private final LoginAttemptGuard loginAttemptGuard;
    private final AccountService accountService;

    /**
     * Đăng ký xong cấp token luôn.
     *
     * <p>Việc ghi database giao hết cho {@code accountService.register} để nó chạy trong một
     * transaction duy nhất; hàm này chỉ còn phần phát token. Nhờ vậy thao tác Redis nằm ngoài
     * transaction database, không giữ connection trong lúc đi mạng.</p>
     *
     * <p>Phải đọc lại tài khoản sau khi tạo thay vì dùng luôn DTO trả về: {@code AccountPrincipal}
     * cần cả entity vai trò để dựng danh sách quyền, mà {@code AccountResponse} chỉ có tên vai
     * trò dạng chuỗi. Một câu SELECT thêm cho mỗi lần đăng ký là cái giá rẻ để không phải chuyền
     * entity xuyên qua ranh giới hai module.</p>
     */
    @Override
    public LoginResponse register(AccountRegistrationRequest request) {
        UUID accountId = accountService.register(request).id();

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account no longer exists"));

        return issueTokens(AccountPrincipal.from(account));
    }

    @Override
    public LoginResponse login(LoginRequest request, String clientIp) {
        // Chặn từ vòng ngoài: đang bị khoá thì khỏi tốn một lần so khớp BCrypt (~100ms CPU),
        // nếu không thì chính cơ chế chống dò lại trở thành đòn bẩy để làm nghẽn server.
        loginAttemptGuard.assertNotLocked(request.email(), clientIp);

        AccountPrincipal principal = authenticate(request, clientIp);

        // Đăng nhập đúng ⇒ xoá bộ đếm, mấy lần gõ nhầm trước đó không được cộng dồn sang phiên sau.
        loginAttemptGuard.reset(request.email());

        return issueTokens(principal);
    }

    /**
     * KHÔNG gắn {@code @Transactional}: thân hàm phần lớn là gọi Redis, bọc transaction vào chỉ
     * giữ connection database rảnh rỗi suốt thời gian đi mạng. Hơn nữa nhãn {@code readOnly = true}
     * cũ là sai tín hiệu — hàm này thu hồi rồi phát lại phiên, tức có GHI.
     *
     * <p>Bỏ được là nhờ {@code findById} đã khai {@code @EntityGraph({"role"})}: role nạp sẵn
     * trong transaction riêng của repository nên {@code AccountPrincipal.from} đọc thoải mái sau
     * khi entity đã detach, không dính {@code LazyInitializationException} dù
     * {@code open-in-view = false}.</p>
     */
    @Override
    public LoginResponse refresh(List<String> tokenCandidates) {
        String sessionId = firstUsableRefreshClaims(tokenCandidates).getId();
        String userId = refreshTokenRedis.getUserIdByJti(sessionId)
                .orElseThrow(this::invalidRefreshToken);

        // Hỏi lại DB thay vì tin vào claim: trong 7 ngày sống của refresh token, tài khoản có thể đã
        // bị xoá, bị khoá hoặc đổi role. Không có bước này thì role cũ sẽ được cấp lại cho 15 phút tiếp theo.
        AccountEntity account = accountRepository.findById(UUID.fromString(userId))
                .orElseThrow(this::invalidRefreshToken);

        AccountPrincipal principal = AccountPrincipal.from(account);

        // Tài khoản bị khoá/vô hiệu hoá thì KHÔNG được cấp token mới. Thiếu chốt này, việc khoá tài
        // khoản gần như vô nghĩa: người bị khoá tuy không đăng nhập lại được nhưng vẫn lấy refresh
        // token cũ ra đổi access token mới, lặp suốt 7 ngày. Hai hàm dưới vốn chỉ được
        // DaoAuthenticationProvider gọi ở luồng login, nhánh này phải tự gọi.
        // Thu hồi sạch luôn thay vì chỉ từ chối, để mọi thiết bị còn lại rụng theo ngay lập tức.
        if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
            refreshTokenRedis.revokeEverySession(userId, account.getEmail());
            throw invalidRefreshToken();
        }

        // XOAY VÒNG: thu hồi token vừa dùng TRƯỚC khi phát token mới. Nhờ vậy mỗi chuỗi refresh
        // token chỉ xài được đúng một lần — kẻ trộm chép lại token cũ sẽ nhận 401 ngay.
        refreshTokenRedis.revoke(sessionId);

        return issueTokens(principal);
    }

    /** Một câu trả lời duy nhất cho mọi lý do refresh thất bại (dùng ở 3 nhánh của {@code refresh}). */
    private ApiException invalidRefreshToken() {
        return new ApiException(ErrorCode.UNAUTHORIZED, "Invalid or expired refresh token");
    }

    /**
     * Phát một phiên mới hoàn chỉnh. Dùng chung cho login và refresh để hai đường không bao giờ
     * lệch nhau về TTL, định dạng claim hay nội dung response.
     *
     * <p>{@code sessionId} vừa là {@code jti} của refresh token, vừa là claim {@code sid} của
     * access token — sợi dây buộc hai token lại để logout giết được cả hai cùng lúc.</p>
     */
    private LoginResponse issueTokens(AccountPrincipal principal) {
        String userId = principal.getId().toString();
        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtService.generateAccessToken(userId, principal.getEmail(), authorities, sessionId);
        String refreshToken = jwtService.generateRefreshToken(userId, principal.getEmail(), sessionId);

        // Refresh token BẮT BUỘC phải nằm trong Redis thì lần /auth/refresh sau mới xác minh được.
        // Redis chết thì DataAccessResourceFailureException bay thẳng lên GlobalExceptionHandler -> 503.
        refreshTokenRedis.store(sessionId, userId, Duration.ofMillis(jwtProperties.getRefreshTtlMs()));

        // Chuẩn OAuth2 quy định expires_in tính bằng GIÂY, trong khi JwtProperties lưu mili giây
        long expiresInSeconds = jwtProperties.getAccessTtlMs() / 1000;

        // "Bearer" là chuẩn RFC 6750 — client gắn header "Authorization: Bearer {accessToken}".
        return authMapper.toLoginResponse(principal, accessToken, refreshToken, "Bearer", expiresInSeconds);
    }

    @Override
    public void logout(List<String> tokenCandidates) {
        String sessionId = extractSessionId(firstRevocableClaims(tokenCandidates));
        if (sessionId != null) {
            // Xoá refresh token của riêng phiên này. Access token cùng phiên chết theo ngay lập tức
            // vì JwtAuthenticationFilter kiểm tra claim "sid" có còn trong Redis hay không.
            refreshTokenRedis.revoke(sessionId);
        }
    }

    @Override
    public void logoutAll(List<String> tokenCandidates) {
        Claims claims = firstRevocableClaims(tokenCandidates);
        refreshTokenRedis.revokeEverySession(claims.get("uid", String.class), claims.getSubject());
    }

    /**
     * Nguồn token đầu tiên <b>thu hồi được</b>, dùng cho logout và logout-all.
     *
     * <p>Không nguồn nào đọc nổi thì ném 401 chứ KHÔNG trả 200 cho xong: logout là thao tác an
     * ninh, người dùng bấm vì nghi bị lộ tài khoản. Báo thành công trong khi phiên của kẻ trộm
     * vẫn sống là tệ hơn hẳn việc bắt họ đăng nhập lại rồi bấm lại.</p>
     */
    private Claims firstRevocableClaims(List<String> tokenCandidates) {
        if (tokenCandidates != null) {
            for (String token : tokenCandidates) {
                Claims claims = parseAllowingExpired(token);
                if (claims != null) {
                    return claims;
                }
            }
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED,
                "No readable token found to revoke. Please sign in again.");
    }

    /**
     * Nguồn đầu tiên là refresh token <b>còn hạn</b>. Access token lẫn trong danh sách bị bỏ qua
     * chứ không làm hỏng cả lượt gọi.
     *
     * <p>Mọi lý do thất bại — thiếu token, chữ ký sai, hết hạn, gửi nhầm access token, phiên đã
     * thu hồi — đều trả về cùng một câu; nói rõ sai ở đâu chỉ giúp kẻ tấn công dò nhanh hơn.</p>
     */
    private Claims firstUsableRefreshClaims(List<String> tokenCandidates) {
        if (tokenCandidates != null) {
            for (String token : tokenCandidates) {
                Claims claims = parseStrictly(token);
                if (claims != null
                        && "refresh".equals(claims.get("typ", String.class))
                        && claims.getId() != null) {
                    return claims;
                }
            }
        }
        throw invalidRefreshToken();
    }

    /**
     * Bản <b>dễ tính</b>: token hết hạn vẫn trả claims. Chỉ dùng cho logout.
     *
     * <p>Không hở bảo mật: JJWT xác minh chữ ký XONG rồi mới kiểm tra hạn, nên tới được
     * {@code ExpiredJwtException} nghĩa là chữ ký đã hợp lệ và claims bên trong đáng tin.
     * Cần nới chỗ này vì access token hết hạn KHÔNG đồng nghĩa phiên đã chết — refresh token
     * của phiên đó còn sống 7 ngày và các thiết bị khác vẫn đang đăng nhập.</p>
     */
    private Claims parseAllowingExpired(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            return jwtService.parseClaims(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Bản <b>khó tính</b>: hết hạn là loại. Dùng cho refresh — chỗ thực sự cấp quyền mới nên
     * không được phép nới lỏng.
     *
     * <p>Chỉ nuốt lỗi của JWT. Redis chết vẫn phải bật lên 503 để client biết server chưa
     * xử lý xong, không được âm thầm coi như token hỏng.</p>
     */
    private Claims parseStrictly(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            return jwtService.parseClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Định danh phiên nằm ở hai chỗ khác nhau tuỳ loại token client gửi lên:
     * refresh token để ở {@code jti}, access token để ở claim {@code sid}.
     * Chấp nhận cả hai nên client lỡ gửi nhầm loại nào thì logout vẫn chạy đúng.
     */
    private String extractSessionId(Claims claims) {
        return claims.getId() != null ? claims.getId() : claims.get("sid", String.class);
    }

    /**
     * Giao toàn bộ việc kiểm tra cho {@code DaoAuthenticationProvider} (so khớp BCrypt, kiểm tra
     * trạng thái tài khoản, chống timing-attack), ở đây chỉ dịch exception của Spring Security
     * sang {@link ApiException} để {@code GlobalExceptionHandler} trả về đúng format {@code ApiResponse}.
     */
    private AccountPrincipal authenticate(LoginRequest request, String clientIp) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
            return (AccountPrincipal) authentication.getPrincipal();

        } catch (AccountStatusException ex) {
            // Tài khoản bị khoá / vô hiệu hoá / hết hạn — AccountPrincipal đã đọc cột status và
            // lockedUntil nên nhánh này chạy thật, do chính DaoAuthenticationProvider ném ra.
            // KHÔNG trả ex.getMessage() ("User is disabled", "User account is locked"...) vì chuỗi đó
            // xác nhận tài khoản CÓ TỒN TẠI → hở đường dò tài khoản y như nhánh 401 bên dưới.
            throw new ApiException(ErrorCode.FORBIDDEN, "Account is not allowed to sign in");

        } catch (AuthenticationException ex) {
            // Đếm cả trường hợp email không tồn tại: nếu chỉ đếm khi sai mật khẩu thì kẻ tấn công
            // vẫn dò được DANH SÁCH email không giới hạn, rồi mới tập trung đánh vào tài khoản thật.
            loginAttemptGuard.recordFailure(request.email(), clientIp);

            // Sai email và sai password phải trả về Y HỆT một chuỗi ⇒ chống dò tài khoản
            // (user enumeration). Spring đã gộp sẵn UsernameNotFoundException vào
            // BadCredentialsException nhờ hideUserNotFoundExceptions = true.
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
    }
}
