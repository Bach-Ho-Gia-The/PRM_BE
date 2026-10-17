package com.structure.springboot.infrastructure.external.cloudinary;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperties(
        String cloudName,
        String apiKey,
        String apiSecret,
        String folderPrefix,
        DataSize maxFileSize,
        Set<String> allowedFormats,
        Duration timeout
) {

    public CloudinaryProperties {
        if (folderPrefix == null
                || !folderPrefix.matches("[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*")) {
            throw new IllegalArgumentException(
                    "cloudinary.folder-prefix must be a safe, non-empty path"
            );
        }

        if (maxFileSize == null
                || maxFileSize.toBytes() <= 0
                || maxFileSize.toBytes() >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "cloudinary.max-file-size must be positive and below 2 GiB"
            );
        }

        if (allowedFormats == null || allowedFormats.isEmpty()) {
            throw new IllegalArgumentException(
                    "cloudinary.allowed-formats must not be empty"
            );
        }

        allowedFormats = allowedFormats.stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .map(value -> "jpeg".equals(value) ? "jpg" : value)
                .collect(Collectors.toUnmodifiableSet());

        if (!Set.of("jpg", "png", "webp").containsAll(allowedFormats)) {
            throw new IllegalArgumentException(
                    "Supported image formats: jpg, jpeg, png, webp"
            );
        }

        if (timeout == null
                || timeout.toSeconds() < 1
                || timeout.toSeconds() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "cloudinary.timeout must be between 1s and 2147483647s"
            );
        }
    }

    public boolean configured() {
        return StringUtils.hasText(cloudName)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(apiSecret);
    }

    // Tránh lộ credentials khi vô tình log đối tượng cấu hình.
    @Override
    public String toString() {
        return "CloudinaryProperties[credentials=REDACTED]";
    }
}
