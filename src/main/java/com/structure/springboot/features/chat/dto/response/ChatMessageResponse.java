package com.structure.springboot.features.chat.dto.response;

import com.structure.springboot.features.chat.enums.SenderType;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        SenderType senderType,
        String content,
        Instant createdAt
) {
}
