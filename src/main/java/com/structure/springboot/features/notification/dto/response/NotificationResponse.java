package com.structure.springboot.features.notification.dto.response;

import com.structure.springboot.features.notification.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String content,
        UUID referenceId,
        boolean isRead,
        Instant createdAt
) {
}
