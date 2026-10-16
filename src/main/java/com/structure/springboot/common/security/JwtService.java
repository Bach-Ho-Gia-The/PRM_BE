package com.structure.springboot.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        // Đọc trực tiếp byte UTF-8, an toàn với mọi định dạng chuỗi secret >= 256 bits
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param sessionId jti của refresh token cùng phiên. Nhúng vào claim {@code sid} để khi phiên
     *                  bị logout thì access token chết theo ngay, không sống hết 15 phút.
     */
    public String generateAccessToken(String userId, String email, Collection<String> authorities, String sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getAccessTtlMs(), ChronoUnit.MILLIS);

        String scope = (authorities != null) ? String.join(" ", authorities) : "";

        return Jwts.builder()
                .subject(email)
                .claim("uid", userId)
                .claim("typ", "access")
                .claim("scope", scope)
                .claim("sid", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(String userId, String email, String jti) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getRefreshTtlMs(), ChronoUnit.MILLIS);

        return Jwts.builder()
                .id(jti)
                .subject(email)
                .claim("uid", userId)
                .claim("typ", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
