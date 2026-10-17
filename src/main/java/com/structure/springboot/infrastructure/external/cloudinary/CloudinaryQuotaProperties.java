package com.structure.springboot.infrastructure.external.cloudinary;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cloudinary.quota")
public record CloudinaryQuotaProperties(
        boolean enabled,
        double blockAtPercent,
        Duration cacheTtl,
        Duration retryDelay
) {

    public CloudinaryQuotaProperties {
        if (!Double.isFinite(blockAtPercent)
                || blockAtPercent <= 0
                || blockAtPercent > 100) {
            throw new IllegalArgumentException(
                    "cloudinary.quota.block-at-percent must be greater than 0 and at most 100"
            );
        }

        validateDuration(cacheTtl, "cache-ttl");
        validateDuration(retryDelay, "retry-delay");
    }

    private static void validateDuration(Duration duration, String name) {
        if (duration == null
                || duration.compareTo(Duration.ofSeconds(1)) < 0
                || duration.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException(
                    "cloudinary.quota." + name + " must be between 1s and 1d"
            );
        }
    }
}
