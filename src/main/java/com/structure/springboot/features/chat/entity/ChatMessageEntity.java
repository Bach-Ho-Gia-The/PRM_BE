package com.structure.springboot.features.chat.entity;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.chat.enums.SenderType;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "chat_messages",
        // Truy vấn xương sống: tin của một hội thoại, mới hơn một mốc thời gian.
        indexes = @Index(name = "idx_chat_messages_conversation",
                columnList = "conversation_id, created_at")
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SenderType senderType;

    /** Ai gõ. Với tin của shop thì đây là admin cụ thể đã trả lời. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private AccountEntity sender;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;
}
