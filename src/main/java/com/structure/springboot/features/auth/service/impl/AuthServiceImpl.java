package com.structure.springboot.features.auth.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.JwtProperties;
import com.structure.springboot.common.security.JwtService;
import com.structure.springboot.common.security.RefreshTokenRedis;
import com.structure.springboot.features.account.dto.request.AccountRegistrationRequest;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.enums.AccountStatus;
import com.structure.springboot.features.account.service.AccountService;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.common.security.OtpProperties;
import com.structure.springboot.features.auth.dto.request.ForgotPasswordRequest;
import com.structure.springboot.features.auth.dto.request.GoogleLoginRequest;
import com.structure.springboot.features.auth.dto.request.LoginRequest;
import com.structure.springboot.features.auth.dto.request.ResetPasswordRequest;
import com.structure.springboot.features.auth.dto.request.VerifyOtpRequest;
import com.structure.springboot.features.auth.dto.request.VerifyRegistrationRequest;
import com.structure.springboot.features.auth.dto.response.LoginResponse;
import com.structure.springboot.features.auth.dto.response.OtpTicketResponse;
import com.structure.springboot.features.auth.dto.response.RegistrationPendingResponse;
import com.structure.springboot.features.auth.security.GoogleTokenVerifier;
import com.structure.springboot.features.auth.security.GoogleUserInfo;
import com.structure.springboot.features.auth.enums.OtpPurpose;
import com.structure.springboot.features.auth.security.OtpRedis;
import com.structure.springboot.features.auth.security.PendingRegistration;
import com.structure.springboot.features.auth.security.PendingRegistrationRedis;
import com.structure.springboot.infrastructure.external.brevo.BrevoMailClient;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
    private final OtpRedis otpRedis;
    private final PendingRegistrationRedis pendingRegistrationRedis;
    private final PasswordEncoder passwordEncoder;
    private final BrevoMailClient brevoMailClient;
    private final OtpProperties otpProperties;
    private final GoogleTokenVerifier googleTokenVerifier;

    // Khởi tạo một lần rồi dùng lại, theo đúng tiền lệ của OrderServiceImpl: gieo hạt cho
    // SecureRandom là thao tác đắt, tạo mới mỗi lần gọi là phí mà không an toàn hơn.
    private final SecureRandom random = new SecureRandom();

    /**
     * Bước 1: cất hồ sơ vào Redis và gửi mã. KHÔNG chạm vào database ngoài một câu kiểm trùng
     * email, nên đăng ký bỏ dở không để lại bất cứ thứ gì phải đi dọn.
     */
    @Override
    public RegistrationPendingResponse register(AccountRegistrationRequest request) {
        String email = normalizeEmail(request.email());

        // Báo trùng email NGAY, đừng để khách ngồi chờ mã rồi mới nói. Bước xác minh sẽ kiểm
        // lại lần nữa để bịt khoảng hở 10 phút giữa hai bước.
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.CONFLICT, "Email already in use: " + email);
        }

        long waitSeconds = otpRedis.cooldownRemaining(OtpPurpose.REGISTER, email);
        if (waitSeconds > 0) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Please wait " + waitSeconds + " more seconds before requesting a new code");
        }
        if (otpRedis.incrementSendCount(OtpPurpose.REGISTER, email) > otpProperties.maxSendsPerHour()) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many verification emails requested for this address. Please try again later.");
        }

        Duration ttl = Duration.ofSeconds(otpProperties.registerTtlSeconds());

        // Mỗi lần bấm sinh một mã phiên RIÊNG. Nhờ vậy hai người cùng khai một địa chỉ không
        // dùng chung một ô: mã gửi tới hộp thư chỉ mở được đúng hồ sơ đã sinh ra nó.
        String registrationId = UUID.randomUUID().toString();

        // Băm ngay tại đây rồi mới rời khỏi service: Redis không bao giờ giữ mật khẩu thô.
        pendingRegistrationRedis.store(registrationId, new PendingRegistration(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.phone()), ttl);

        String otp = generateOtp();
        otpRedis.storeCode(OtpPurpose.REGISTER, registrationId, otp, ttl);
        brevoMailClient.sendRegistrationOtpEmail(email, otp, otpProperties.registerTtlSeconds());

        // Chỉ bật khoảng chờ SAU KHI mail đi thành công. Bật trước thì Brevo lỗi là khách vừa
        // nhận 502 vừa bị khoá 20 giây dù chưa hề có mail nào tới.
        otpRedis.startCooldown(OtpPurpose.REGISTER, email,
                Duration.ofSeconds(otpProperties.resendCooldownSeconds()));

        return authMapper.toRegistrationPendingResponse(registrationId, email,
                otpProperties.registerTtlSeconds(), otpProperties.resendCooldownSeconds());
    }

    @Override
    public LoginResponse verifyRegistration(VerifyRegistrationRequest request) {
        String registrationId = request.registrationId();

        PendingRegistration pending = pendingRegistrationRedis.find(registrationId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "Your registration session has expired. Please sign up again."));

        requireValidOtp(OtpPurpose.REGISTER, registrationId, request.otp(),
                Duration.ofSeconds(otpProperties.registerTtlSeconds()),
                // Hết lượt thì huỷ CẢ hồ sơ chứ không chỉ huỷ mã: yêu cầu là "coi như tạo tài
                // khoản thất bại", nên người dùng phải khai lại từ đầu.
                () -> {
                    pendingRegistrationRedis.clear(registrationId);
                    throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                            "Too many incorrect attempts. Your registration was cancelled, please sign up again.");
                });

        UUID accountId;
        try {
            // Chỉ tới dòng này database mới bị động tới lần đầu tiên.
            accountId = accountService.createFromVerifiedRegistration(
                    pending.email(), pending.passwordHash(), pending.fullName(), pending.phone()).id();
        } catch (ApiException ex) {
            // Thường là 409 vì trong lúc chờ đã có người chiếm email. Phải dọn hồ sơ, không thì
            // khách bấm lại bao nhiêu lần cũng nhận đúng lỗi đó cho tới khi hết hạn.
            // Chỉ bắt ApiException chứ không bắt RuntimeException: lỗi hạ tầng chớp nhoáng
            // (database rớt một nhịp) là thứ thử lại được, xoá hồ sơ trong trường hợp đó là
            // bắt khách khai lại từ đầu dù họ không làm gì sai.
            pendingRegistrationRedis.clear(registrationId);
            throw ex;
        }

        pendingRegistrationRedis.clear(registrationId);

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account no longer exists"));

        return issueTokens(AccountPrincipal.from(account));
    }

    @Override
    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        // Xác minh TRƯỚC khi chạm vào database: token hỏng thì không được phép tạo ra bất cứ
        // bản ghi nào. Mọi thông tin dùng phía dưới đều bóc từ token đã ký, không lấy từ body.
        GoogleUserInfo googleUser = googleTokenVerifier.verify(request.idToken());

        // Chặn TRƯỚC khi gộp: tài khoản đang bị khoá thì không được phép ghi provider_id vào
        // bản ghi của họ rồi mới từ chối. Tra theo đúng thứ tự mà upsert sẽ dùng.
        accountRepository.findByProviderId(googleUser.providerId())
                .or(() -> accountRepository.findByEmailIgnoreCase(googleUser.email()))
                .ifPresent(this::assertSignInAllowed);

        UUID accountId = accountService.upsertGoogleAccount(
                googleUser.providerId(),
                googleUser.email(),
                googleUser.fullName(),
                googleUser.avatarUrl()).id();

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account no longer exists"));

        // Kiểm lại lần nữa dù phía trên đã chặn: nhánh này KHÔNG đi qua DaoAuthenticationProvider
        // nên mọi đường tự cấp token đều phải tự xác nhận hai cờ trạng thái trước khi phát.
        assertSignInAllowed(account);

        return issueTokens(AccountPrincipal.from(account));
    }

    /** Hai cờ trạng thái mà {@code DaoAuthenticationProvider} vốn tự kiểm ở luồng login thường. */
    private void assertSignInAllowed(AccountEntity account) {
        AccountPrincipal principal = AccountPrincipal.from(account);
        if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Account is not allowed to sign in");
        }
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

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        // Tài khoản không tồn tại, hoặc đang DISABLED/LOCKED -> im lặng kết thúc. Controller vẫn
        // trả cùng một câu trả lời nên người ngoài không phân biệt được hai trường hợp.
        accountRepository.findByEmailIgnoreCase(request.email())
                .filter(account -> account.getStatus() == null || account.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(account -> issueOtp(account.getEmail()));
    }

    @Override
    public OtpTicketResponse verifyOtp(VerifyOtpRequest request) {
        String key = normalizeEmail(request.email());

        requireValidOtp(OtpPurpose.RESET, key, request.otp(),
                Duration.ofSeconds(otpProperties.ttlSeconds()),
                () -> {
                    throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                            "Too many incorrect attempts. Please request a new code.");
                });

        // Mã đã chết trong requireValidOtp; đổi lấy vé tạm sống lâu hơn một chút cho đủ thời
        // gian gõ mật khẩu mới.
        String ticket = otpRedis.createTicket(key,
                Duration.ofSeconds(otpProperties.verifiedTtlSeconds()));

        return authMapper.toOtpTicketResponse(ticket, otpProperties.verifiedTtlSeconds());
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String email = otpRedis.consumeTicket(request.ticket())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED,
                        "The reset session has expired. Please start again."));

        // Ghi database và thu hồi phiên gom trong accountService để dùng chung đúng một đường
        // với changePassword, khỏi sinh ra hai bản xử lý dễ lệch nhau.
        accountService.resetPassword(email, request.newPassword());
    }

    /**
     * Phát một mã mới rồi gửi mail. Tách khỏi {@code forgotPassword} để phần quyết định
     * "có được phát không" nằm gọn một chỗ.
     *
     * <p>Hai lớp hãm, đều khoá theo email: khoảng chờ giữa hai lần bấm, và trần số lần gửi trong
     * một giờ. Thiếu chúng thì bất kỳ ai cũng biến endpoint công khai này thành máy dội mail vào
     * hộp thư người khác.</p>
     */
    private void issueOtp(String email) {
        String key = normalizeEmail(email);

        // Im lặng bỏ qua chứ KHÔNG ném 429 như luồng đăng ký: endpoint quên mật khẩu phải trả
        // cùng một kết quả cho mọi email, báo "đang trong thời gian chờ" là xác nhận email đó
        // có thật trong hệ thống.
        if (otpRedis.cooldownRemaining(OtpPurpose.RESET, key) > 0
                || otpRedis.incrementSendCount(OtpPurpose.RESET, key) > otpProperties.maxSendsPerHour()) {
            return;
        }

        String otp = generateOtp();
        otpRedis.storeCode(OtpPurpose.RESET, key, otp, Duration.ofSeconds(otpProperties.ttlSeconds()));

        try {
            brevoMailClient.sendOtpEmail(email, otp, otpProperties.ttlSeconds());
        } catch (ApiException ex) {
            // Nuốt lỗi ở ĐÚNG luồng này thôi. Endpoint quên mật khẩu đã cam kết luôn trả 200;
            // để lỗi gửi mail bay lên là email có thật trả 502 còn email bịa trả 200, tức là
            // tặng kẻ tấn công đúng cái máy dò mà cam kết kia sinh ra để chặn.
            // Đổi lại, cấu hình Brevo sai sẽ im lặng ở đây — /auth/register vẫn báo lỗi rõ ràng
            // nên vẫn có chỗ phát hiện.
            return;
        }

        // Bật khoảng chờ SAU khi mail đi thành công — xem lý do ở register().
        otpRedis.startCooldown(OtpPurpose.RESET, key,
                Duration.ofSeconds(otpProperties.resendCooldownSeconds()));
    }

    /**
     * Kiểm một mã OTP: đúng thì xoá mã rồi trả về, sai thì đếm và ném lỗi. Dùng chung cho cả
     * luồng đăng ký lẫn luồng quên mật khẩu — trước đó hai nơi chép y hệt nhau mười mấy dòng.
     *
     * <p>{@code onAttemptsExhausted} để mỗi luồng tự quyết phần dọn dẹp riêng và tự ném câu lỗi
     * của mình; hàm này chỉ lo phần đếm và xoá mã.</p>
     *
     * <p>Hết lượt là <b>xoá hẳn mã</b> chứ không chặn tạm: giữ lại mã nghĩa là kẻ dò chỉ cần đợi
     * bộ đếm hết hạn rồi đoán tiếp trên đúng mã cũ.</p>
     *
     * <p><b>🔴 Thứ tự ĐẾM TRƯỚC — SO SAU là bắt buộc, đừng đảo lại.</b> Bản cũ so mã trước rồi
     * mới tăng bộ đếm, nên một loạt request bắn cùng lúc đều kịp đọc mã và so <i>trước khi</i>
     * bộ đếm kịp chạm trần: 5 lượt đoán trên giấy biến thành hàng trăm lượt đoán thật, đúng bằng
     * số luồng mà server xử lý song song được. {@code INCR} của Redis là nguyên tử nên mỗi
     * request nhận một số thứ tự riêng; chặn ngay tại số thứ tự thì request thứ sáu trở đi không
     * bao giờ chạm tới phép so, dù chúng tới cùng một mili giây.</p>
     */
    private void requireValidOtp(OtpPurpose purpose, String subject, String submittedOtp,
                                 Duration ttl, Runnable onAttemptsExhausted) {
        long attempts = otpRedis.incrementAttempts(purpose, subject, ttl);

        // Cổng chống đua: quá trần thì dừng TRƯỚC khi so, không cho biết mã đúng hay sai.
        if (attempts > otpProperties.maxAttempts()) {
            otpRedis.clearCode(purpose, subject);
            onAttemptsExhausted.run();
        }

        String storedOtp = otpRedis.findCode(purpose, subject)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "The verification code has expired. Please request a new one."));

        if (storedOtp.equals(submittedOtp)) {
            otpRedis.clearCode(purpose, subject);
            return;
        }

        // Cổng kết thúc lượt: sai đúng ở lần thứ maxAttempts thì huỷ luôn tại đây, để người dùng
        // nhận ngay câu "đã huỷ" chứ không phải bấm thêm một lần nữa mới biết.
        if (attempts >= otpProperties.maxAttempts()) {
            otpRedis.clearCode(purpose, subject);
            onAttemptsExhausted.run();
        }

        throw new ApiException(ErrorCode.BAD_REQUEST, "Incorrect verification code");
    }

    /**
     * {@code %06d} để mã luôn đủ 6 chữ số: {@code nextInt(1_000_000)} trả 42 thì phải thành
     * "000042", không thì độ dài mã thay đổi và regex ở cả client lẫn backend đều trượt.
     */
    private String generateOtp() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    /** Khoá Redis phải khớp tuyệt đối, mà đăng nhập lại so email không phân biệt hoa thường. */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
