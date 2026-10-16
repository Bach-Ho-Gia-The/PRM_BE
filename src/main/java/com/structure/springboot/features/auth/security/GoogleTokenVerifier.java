package com.structure.springboot.features.auth.security;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.GoogleAuthProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Xác minh ID token do Google phát hành.
 *
 * <p><b>Bốn thứ bắt buộc phải kiểm</b> (theo tài liệu OpenID Connect của Google) — thiếu bất kỳ
 * cái nào là thủng:</p>
 * <ol>
 *   <li><b>Chữ ký RS256</b> — đối chiếu với khoá công khai tại {@code JWK_SET_URI}.</li>
 *   <li><b>{@code aud} == client id của mình</b> — bỏ qua thì ID token của BẤT KỲ ứng dụng Google
 *       nào cũng dùng để đăng nhập vào đây được, đây là lỗ hổng nặng nhất của luồng này.</li>
 *   <li><b>{@code iss}</b> thuộc tập hợp Google công bố.</li>
 *   <li><b>{@code exp}</b> chưa quá hạn — {@code JwtValidators.createDefault()} lo.</li>
 * </ol>
 *
 * <p><b>Vì sao {@code withJwkSetUri} chứ không phải {@code JwtDecoders.fromIssuerLocation}:</b>
 * bản {@code fromIssuerLocation} tải tài liệu discovery ngay lúc dựng bean, biến việc khởi động
 * ứng dụng thành phụ thuộc vào mạng — mất mạng là app không lên được, kể cả khi chẳng ai dùng
 * đăng nhập Google. Bản này chỉ gọi mạng ở lần xác minh đầu tiên rồi cache lại.</p>
 */
@Component
public class GoogleTokenVerifier {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    // Google công bố HAI giá trị iss hợp lệ, bản có https là bản hiện hành còn bản trần là bản cũ.
    // JwtIssuerValidator có sẵn chỉ nhận đúng một chuỗi nên phải tự so với cả tập hợp.
    private static final Set<String> VALID_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    private final GoogleAuthProperties properties;
    private final NimbusJwtDecoder jwtDecoder;

    public GoogleTokenVerifier(GoogleAuthProperties properties) {
        this.properties = properties;

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                this::validateIssuer,
                this::validateAudience));
        this.jwtDecoder = decoder;
    }

    public GoogleUserInfo verify(String idToken) {
        if (!StringUtils.hasText(properties.clientId())) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Google sign-in is not configured. Please set GOOGLE_CLIENT_ID.");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException ex) {
            // Gộp mọi lý do thất bại thành một câu: chữ ký sai, hết hạn hay sai aud đều là
            // "token không dùng được", nói chi tiết chỉ giúp người đang dò tìm cách lách.
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid Google credential");
        }

        if (!isEmailVerified(jwt)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Your Google account email has not been verified");
        }

        return new GoogleUserInfo(
                jwt.getSubject(),
                // Chuẩn hoá y như luồng đăng ký thường: Google gần như luôn trả chữ thường
                // nhưng không cam kết, mà email là thứ đi thẳng vào cột unique của database.
                jwt.getClaimAsString("email").trim().toLowerCase(Locale.ROOT),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture")
        );
    }

    private OAuth2TokenValidatorResult validateIssuer(Jwt jwt) {
        return VALID_ISSUERS.contains(jwt.getIssuer() != null ? jwt.getIssuer().toString() : null)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_issuer"));
    }

    private OAuth2TokenValidatorResult validateAudience(Jwt jwt) {
        return jwt.getAudience() != null && jwt.getAudience().contains(properties.clientId())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));
    }

    /**
     * Claim {@code email_verified} được Google trả lúc là boolean thật, lúc là chuỗi "true" tuỳ
     * đường phát hành. Chỉ đọc một kiểu thì sẽ có lúc tài khoản hợp lệ bị chặn oan.
     */
    private boolean isEmailVerified(Jwt jwt) {
        Object claim = jwt.getClaim("email_verified");
        return Boolean.TRUE.equals(claim) || "true".equalsIgnoreCase(String.valueOf(claim));
    }
}
