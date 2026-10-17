package com.structure.springboot.features.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Một dòng trong danh sách hội thoại của admin. */
public record ConversationSummaryResponse(
        UUID id,
        UUID accountId,
        String customerName,
        String customerEmail,
        Instant lastMessageAt,
        String lastMessagePreview,
        int unreadByAdmin
) {
}
