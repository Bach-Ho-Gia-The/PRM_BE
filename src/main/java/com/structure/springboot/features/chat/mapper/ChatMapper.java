package com.structure.springboot.features.chat.mapper;

import com.structure.springboot.features.chat.dto.response.ChatMessageResponse;
import com.structure.springboot.features.chat.dto.response.ConversationResponse;
import com.structure.springboot.features.chat.dto.response.ConversationSummaryResponse;
import com.structure.springboot.features.chat.entity.ChatMessageEntity;
import com.structure.springboot.features.chat.entity.ConversationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChatMapper {

    ChatMessageResponse toMessageResponse(ChatMessageEntity message);

    /** Phía khách chỉ quan tâm số tin CHÍNH MÌNH chưa đọc. */
    @Mapping(target = "unreadCount", source = "unreadByCustomer")
    ConversationResponse toConversationResponse(ConversationEntity conversation);

    /**
     * Tên khách đến từ bảng {@code profiles}, mà {@code ConversationEntity} không có quan hệ tới
     * đó. Truyền vào làm tham số thứ hai để service nạp theo lô một lần, tránh N+1 ở màn danh sách.
     */
    @Mapping(target = "accountId", source = "conversation.account.id")
    @Mapping(target = "customerEmail", source = "conversation.account.email")
    @Mapping(target = "customerName", source = "customerName")
    ConversationSummaryResponse toSummaryResponse(ConversationEntity conversation, String customerName);
}
