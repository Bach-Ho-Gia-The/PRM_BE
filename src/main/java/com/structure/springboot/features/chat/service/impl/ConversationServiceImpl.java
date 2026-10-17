package com.structure.springboot.features.chat.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.chat.dto.request.ChatMessageRequest;
import com.structure.springboot.features.chat.dto.response.ChatMessageResponse;
import com.structure.springboot.features.chat.dto.response.ConversationResponse;
import com.structure.springboot.features.chat.dto.response.ConversationSummaryResponse;
import com.structure.springboot.features.chat.entity.ChatMessageEntity;
import com.structure.springboot.features.chat.entity.ConversationEntity;
import com.structure.springboot.features.chat.enums.SenderType;
import com.structure.springboot.features.chat.mapper.ChatMapper;
import com.structure.springboot.features.chat.repository.ChatMessageRepository;
import com.structure.springboot.features.chat.repository.ConversationRepository;
import com.structure.springboot.features.chat.service.ConversationService;
import com.structure.springboot.features.profile.entity.ProfileEntity;
import com.structure.springboot.features.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int PREVIEW_LENGTH = 200;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final ChatMapper chatMapper;

    // ───────────────────────────── phía khách ─────────────────────────────

    @Override
    @Transactional
    public ConversationResponse getOrCreateMyConversation(UUID accountId) {
        return chatMapper.toConversationResponse(requireMyConversation(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMyMessagesSince(UUID accountId, Instant since) {
        ConversationEntity conversation = conversationRepository.findByAccountId(accountId)
                .orElse(null);
        if (conversation == null) {
            return List.of(); // Chưa nhắn gì thì chưa có hội thoại, không phải lỗi.
        }
        return messagesSince(conversation.getId(), since);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getMyMessageHistory(UUID accountId, Pageable pageable) {
        ConversationEntity conversation = conversationRepository.findByAccountId(accountId)
                .orElse(null);
        if (conversation == null) {
            return PageResponse.from(org.springframework.data.domain.Page.empty(pageable));
        }
        return history(conversation.getId(), pageable);
    }

    @Override
    @Transactional
    public ChatMessageResponse sendAsCustomer(UUID accountId, ChatMessageRequest request) {
        ConversationEntity conversation = requireMyConversation(accountId);
        return chatMapper.toMessageResponse(
                append(conversation, conversation.getAccount(), SenderType.CUSTOMER, request.content()));
    }

    @Override
    @Transactional
    public ConversationResponse markReadByCustomer(UUID accountId) {
        ConversationEntity conversation = requireMyConversation(accountId);
        conversation.setUnreadByCustomer(0);
        return chatMapper.toConversationResponse(conversationRepository.save(conversation));
    }

    // ───────────────────────────── phía admin ─────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConversationSummaryResponse> getAdminConversations(Pageable pageable) {
        var page = conversationRepository.findByLastMessageAtNotNullOrderByLastMessageAtDesc(pageable);
        // Nạp tên khách theo LÔ: tra từng dòng là N+1 đúng nghĩa ở màn danh sách.
        Set<UUID> accountIds = page.getContent().stream()
                .map(conversation -> conversation.getAccount().getId()).collect(Collectors.toSet());
        Map<UUID, String> names = accountIds.isEmpty() ? Map.of()
                : profileRepository.findByAccountIdIn(accountIds).stream()
                        .filter(profile -> profile.getFullName() != null)
                        .collect(Collectors.toMap(profile -> profile.getAccount().getId(),
                                ProfileEntity::getFullName, (first, second) -> first));
        return PageResponse.from(page.map(conversation -> chatMapper.toSummaryResponse(
                conversation, names.get(conversation.getAccount().getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getAdminMessagesSince(UUID conversationId, Instant since) {
        return messagesSince(requireConversation(conversationId).getId(), since);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getAdminMessageHistory(UUID conversationId,
                                                                     Pageable pageable) {
        return history(requireConversation(conversationId).getId(), pageable);
    }

    @Override
    @Transactional
    public ChatMessageResponse sendAsAdmin(UUID adminAccountId, UUID conversationId,
                                           ChatMessageRequest request) {
        ConversationEntity conversation = requireConversation(conversationId);
        AccountEntity admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account no longer exists"));
        return chatMapper.toMessageResponse(
                append(conversation, admin, SenderType.ADMIN, request.content()));
    }

    @Override
    @Transactional
    public ConversationSummaryResponse markReadByAdmin(UUID conversationId) {
        ConversationEntity conversation = requireConversation(conversationId);
        conversation.setUnreadByAdmin(0);
        conversationRepository.save(conversation);
        return chatMapper.toSummaryResponse(conversation, profileRepository
                .findByAccountId(conversation.getAccount().getId())
                .map(ProfileEntity::getFullName).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public long countConversationsAwaitingAdmin() {
        return conversationRepository.countByUnreadByAdminGreaterThan(0);
    }

    // ───────────────────────────── dùng chung ─────────────────────────────

    /**
     * ĐƯỜNG GHI TIN NHẮN DUY NHẤT.
     *
     * <p>Ba field lặp dữ liệu trên hội thoại chỉ đúng khi mọi tin đều đi qua đây. Thêm đường ghi
     * thứ hai là số chưa đọc và dòng xem trước bắt đầu lệch.</p>
     */
    private ChatMessageEntity append(ConversationEntity conversation, AccountEntity sender,
                                     SenderType senderType, String content) {
        String text = content.trim();
        ChatMessageEntity message = chatMessageRepository.save(ChatMessageEntity.builder()
                .conversation(conversation)
                .sender(sender)
                .senderType(senderType)
                .content(text)
                .build());

        conversation.setLastMessageAt(Instant.now());
        conversation.setLastMessagePreview(text.length() > PREVIEW_LENGTH
                ? text.substring(0, PREVIEW_LENGTH) : text);
        if (senderType == SenderType.CUSTOMER) {
            conversation.setUnreadByAdmin(conversation.getUnreadByAdmin() + 1);
            conversation.setUnreadByCustomer(0); // mình vừa gõ thì coi như đã đọc hết
        } else {
            conversation.setUnreadByCustomer(conversation.getUnreadByCustomer() + 1);
            conversation.setUnreadByAdmin(0);
        }
        conversationRepository.save(conversation);
        return message;
    }

    private List<ChatMessageResponse> messagesSince(UUID conversationId, Instant since) {
        Instant from = since == null ? Instant.EPOCH : since;
        return chatMessageRepository
                .findByConversationIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(conversationId, from)
                .stream().map(chatMapper::toMessageResponse).toList();
    }

    private PageResponse<ChatMessageResponse> history(UUID conversationId, Pageable pageable) {
        return PageResponse.from(chatMessageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(chatMapper::toMessageResponse));
    }

    /**
     * Hội thoại của khách, tạo nếu chưa có.
     *
     * <p>Hai request vào cùng lúc thì ràng buộc {@code unique} trên {@code account_id} chặn bản
     * thứ hai — bắt lỗi rồi đọc lại thay vì để request đó hỏng.</p>
     */
    private ConversationEntity requireMyConversation(UUID accountId) {
        return conversationRepository.findByAccountId(accountId).orElseGet(() -> {
            AccountEntity account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account no longer exists"));
            try {
                return conversationRepository.saveAndFlush(
                        ConversationEntity.builder().account(account).build());
            } catch (DataIntegrityViolationException exception) {
                return conversationRepository.findByAccountId(accountId)
                        .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                                "Could not open the conversation, please try again"));
            }
        });
    }

    private ConversationEntity requireConversation(UUID conversationId) {
        return conversationRepository.findWithAccountById(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Conversation not found with id: " + conversationId));
    }
}
