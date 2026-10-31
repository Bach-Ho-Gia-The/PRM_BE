package com.structure.springboot.common.security;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loi xac thuc phai ra dung MA HTTP nao.
 *
 * <p>Day la loai kiem tra ma tam vong audit auth truoc deu BO SOT: chung chi soi tinh chat tinh
 * cua code (route nao thieu guard, import mo coi, thieu @Transactional) chu khong bao gio truy
 * nguoc "nem exception nay ra thi client nhan ve so may". Bug that nam dung o khe do —
 * GlobalExceptionHandler khong co handler cho JwtException nen moi loi token deu roi xuong
 * @ExceptionHandler(Exception.class) va tra 500 thay vi 401, khien frontend khong bao gio kich
 * hoat auto-refresh.</p>
 *
 * <p>Filter khong tu viet response ma uy quyen cho HandlerExceptionResolver, nen phep thu o day
 * la bat lay DUNG exception filter giao cho resolver: ApiException(UNAUTHORIZED) se thanh 401 qua
 * handler #1, con exception tho se thanh 500 qua handler #8.</p>
 *
 * <p>Nhan test de ASCII thuan vi output surefire ma hoa hong tieng Viet co dau.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterStatusTest {

    @Mock private JwtService jwtService;
    @Mock private RefreshTokenRedis refreshTokenRedis;
    @Mock private HandlerExceptionResolver handlerExceptionResolver;
    @Mock private AuthCookieProperties cookieProperties;
    @Mock private AuthCookieFactory authCookieFactory;
    @Mock private FilterChain filterChain;
    @Mock private Claims claims;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String TOKEN = "a.b.c";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, refreshTokenRedis, handlerExceptionResolver,
                cookieProperties, authCookieFactory);

        request = new MockHttpServletRequest("GET", "/api/v1/accounts/me");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        response = new MockHttpServletResponse();

        when(authCookieFactory.clearAccessTokenCookie()).thenReturn(ResponseCookie.from("at", "").build());
        when(authCookieFactory.clearRefreshTokenCookie()).thenReturn(ResponseCookie.from("rt", "").build());

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Bat exception that su duoc giao cho resolver — thu quyet dinh ma HTTP cuoi cung. */
    private Exception resolved() throws Exception {
        filter.doFilter(request, response, filterChain);
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(handlerExceptionResolver).resolveException(any(), any(), isNull(), captor.capture());
        return captor.getValue();
    }

    private void assertUnauthorized(Exception resolved, String expectedMessage) {
        ApiException api = assertInstanceOf(ApiException.class, resolved,
                "phai la ApiException, neu khong GlobalExceptionHandler se tra 500");
        assertEquals(ErrorCode.UNAUTHORIZED, api.getErrorCode());
        assertEquals(expectedMessage, api.getMessage());
    }

    private void stubValidClaims() {
        when(jwtService.parseClaims(TOKEN)).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn("user@example.com");
        when(claims.getIssuedAt()).thenReturn(new Date());
        when(claims.get("sid", String.class)).thenReturn("sid-1");
        when(claims.get("uid", String.class)).thenReturn(UUID.randomUUID().toString());
    }

    // ─────────── 4 ca bro chot ───────────

    /** 🎯 Ca gay bug: token het han moi 15 phut mot lan, truoc day tra 500. */
    @Test
    void anExpiredTokenMustProduceUnauthorizedNotServerError() throws Exception {
        when(jwtService.parseClaims(TOKEN)).thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertUnauthorized(resolved(), "Access token has expired");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aForgedSignatureMustProduceUnauthorized() throws Exception {
        when(jwtService.parseClaims(TOKEN)).thenThrow(new SignatureException("bad signature"));

        assertUnauthorized(resolved(), "Invalid token");
    }

    @Test
    void aMalformedTokenMustProduceUnauthorized() throws Exception {
        when(jwtService.parseClaims(TOKEN)).thenThrow(new MalformedJwtException("malformed"));

        assertUnauthorized(resolved(), "Invalid token");
    }

    /**
     * 🎯 Mat con lai cua cung dong xu: Redis chet KHONG duoc hoa thanh 401. Neu hoa thanh 401 thi
     * frontend di refresh, refresh cung chet vi cung Redis do, roi da nguoi dung ra man dang nhap
     * trong khi tai khoan ho chang viec gi — su co ha tang bi nguy trang thanh loi nguoi dung.
     */
    @Test
    void aRedisOutageMustStayAServerErrorNotBecomeUnauthorized() throws Exception {
        stubValidClaims();
        QueryTimeoutException outage = new QueryTimeoutException("redis is down");
        when(refreshTokenRedis.isAccessTokenRevoked(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(outage);

        Exception resolved = resolved();

        assertSame(outage, resolved, "phai giu nguyen exception goc de ra 5xx");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ─────────── chong hoi quy cho 3 ca filter tu nem ───────────

    @Test
    void aRefreshTokenUsedAsAnAccessTokenKeepsItsOwnMessage() throws Exception {
        when(jwtService.parseClaims(TOKEN)).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("refresh");

        assertUnauthorized(resolved(), "Invalid token type: expected access token");
    }

    @Test
    void aTokenWithoutSubjectKeepsItsOwnMessage() throws Exception {
        when(jwtService.parseClaims(TOKEN)).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn(null);

        assertUnauthorized(resolved(), "Token is missing the subject claim");
    }

    @Test
    void aRevokedSessionKeepsItsOwnMessage() throws Exception {
        stubValidClaims();
        when(refreshTokenRedis.isAccessTokenRevoked(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);

        assertUnauthorized(resolved(), "Session has been revoked. Please sign in again.");
    }

    /** uid khong phai UUID: AuthenticatedUser.of da doi san sang ApiException, phai giu nguyen. */
    @Test
    void aTokenCarryingAGarbageUidIsRejectedAsMalformed() throws Exception {
        stubValidClaims();
        when(claims.get("uid", String.class)).thenReturn("khong-phai-uuid");
        when(refreshTokenRedis.isAccessTokenRevoked(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(false);

        assertUnauthorized(resolved(), "Malformed token");
    }

    // ─────────── duong thong ───────────

    @Test
    void aHealthyTokenPassesThroughAndPopulatesTheContext() throws Exception {
        stubValidClaims();
        when(claims.get("scope", String.class)).thenReturn("ROLE_USER");
        when(refreshTokenRedis.isAccessTokenRevoked(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /** Khong co token: filter phai de yen cho chuoi chay tiep, de authenticationEntryPoint tra 401. */
    @Test
    void noTokenAtAllIsLeftToSpringSecurityNotRejectedHere() throws Exception {
        MockHttpServletRequest bare = new MockHttpServletRequest("GET", "/api/v1/accounts/me");

        filter.doFilter(bare, response, filterChain);

        verify(filterChain).doFilter(bare, response);
        verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());
    }

    /** Moi duong dan /auth/ phai duoc bo qua, neu khong cookie het han se chan ca viec dang nhap lai. */
    @Test
    void everyAuthPathIsSkippedSoAnExpiredCookieCannotBlockSigningInAgain() {
        for (String path : new String[]{"/auth/login", "/auth/refresh", "/auth/logout", "/auth/register"}) {
            MockHttpServletRequest authRequest = new MockHttpServletRequest("POST", path);
            authRequest.setRequestURI(path);
            assertEquals(true, filter.shouldNotFilter(authRequest), path + " phai duoc bo qua");
        }
    }

    @Test
    void aProtectedPathIsNotSkipped() {
        MockHttpServletRequest protectedRequest = new MockHttpServletRequest("GET", "/accounts/me");
        protectedRequest.setRequestURI("/accounts/me");
        assertEquals(false, filter.shouldNotFilter(protectedRequest));
    }

    /** Loi xac thuc nao cung phai xoa cookie, neu khong trinh duyet con gui lai token chet mai. */
    @Test
    void everyRejectionClearsTheAuthCookies() throws Exception {
        when(jwtService.parseClaims(TOKEN)).thenThrow(new UnsupportedJwtException("unsupported"));

        resolved();

        assertEquals(2, response.getHeaders("Set-Cookie").size());
        verify(authCookieFactory).clearAccessTokenCookie();
        verify(authCookieFactory).clearRefreshTokenCookie();
    }
}
