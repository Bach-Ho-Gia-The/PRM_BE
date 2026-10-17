package com.structure.springboot.features.chat.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.chat.dto.request.ChatMessageRequest;
import com.structure.springboot.features.chat.dto.response.ChatMessageResponse;
import com.structure.springboot.features.chat.dto.response.ConversationResponse;
import com.structure.springboot.features.chat.dto.response.ConversationSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationService {

    /** Lấy hội thoại của mình, TỰ TẠO nếu chưa có. Khách mở màn chat lần đầu không phải gọi 2 lần. */
    ConversationResponse getOrCreateMyConversation(UUID accountId);

    /** {@code since != null} ⇒ chỉ tin mới hơn mốc, tăng dần. {@code null} ⇒ lịch sử, giảm dần. */
    List<ChatMessageResponse> getMyMessagesSince(UUID accountId, Instant since);

    PageResponse<ChatMessageResponse> getMyMessageHistory(UUID accountId, Pageable pageable);

    ChatMessageResponse sendAsCustomer(UUID accountId, ChatMessageRequest request);

    ConversationResponse markReadByCustomer(UUID accountId);

    PageResponse<ConversationSummaryResponse> getAdminConversations(Pageable pageable);

    List<ChatMessageResponse> getAdminMessagesSince(UUID conversationId, Instant since);

    PageResponse<ChatMessageResponse> getAdminMessageHistory(UUID conversationId, Pageable pageable);

    ChatMessageResponse sendAsAdmin(UUID adminAccountId, UUID conversationId, ChatMessageRequest request);

    ConversationSummaryResponse markReadByAdmin(UUID conversationId);

    /** Số hội thoại đang có tin khách gửi mà admin chưa đọc. */
    long countConversationsAwaitingAdmin();
}
