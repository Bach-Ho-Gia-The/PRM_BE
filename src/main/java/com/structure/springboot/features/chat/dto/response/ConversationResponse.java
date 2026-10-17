package com.structure.springboot.features.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Hội thoại nhìn từ phía khách. */
public record ConversationResponse(
        UUID id,
        Instant lastMessageAt,
        String lastMessagePreview,
        int unreadCount,
        Instant createdAt
) {
}
