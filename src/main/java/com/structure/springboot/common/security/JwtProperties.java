package com.structure.springboot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,   // Khai báo trong yaml ví dụ: 15m, 1h
        Duration refreshTtl  // Khai báo trong yaml ví dụ: 7d, 30d
) {
    // Getter phụ trợ chuyển sang milliseconds nếu thư viện JJWT cần long ms
    public long getAccessTtlMs() {
        return accessTtl != null ? accessTtl.toMillis() : 900_000L;
    }

    public long getRefreshTtlMs() {
        return refreshTtl != null ? refreshTtl.toMillis() : 604_800_000L;
    }
}
