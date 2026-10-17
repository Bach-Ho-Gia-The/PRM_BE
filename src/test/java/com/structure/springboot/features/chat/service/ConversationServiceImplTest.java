package com.structure.springboot.features.chat.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.chat.dto.request.ChatMessageRequest;
import com.structure.springboot.features.chat.entity.ChatMessageEntity;
import com.structure.springboot.features.chat.entity.ConversationEntity;
import com.structure.springboot.features.chat.enums.SenderType;
import com.structure.springboot.features.chat.mapper.ChatMapper;
import com.structure.springboot.features.chat.repository.ChatMessageRepository;
import com.structure.springboot.features.chat.repository.ConversationRepository;
import com.structure.springboot.features.chat.service.impl.ConversationServiceImpl;
import com.structure.springboot.features.profile.entity.ProfileEntity;
import com.structure.springboot.features.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nhãn test để ASCII thuần vì output surefire mã hoá hỏng tiếng Việt có dấu. */
@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock private ConversationRepository conversations;
    @Mock private ChatMessageRepository messages;
    @Mock private AccountRepository accounts;
    @Mock private ProfileRepository profiles;
    private ConversationServiceImpl service;

    private AccountEntity customer;
    private final Pageable page = PageRequest.of(0, 30);

    @BeforeEach
    void setUp() {
        ChatMapper mapper = Mappers.getMapper(ChatMapper.class);
        service = new ConversationServiceImpl(conversations, messages, accounts, profiles, mapper);
        customer = new AccountEntity();
        customer.setId(UUID.randomUUID());
        customer.setEmail("khach@example.com");
    }

    private ConversationEntity conversation() {
        ConversationEntity entity = ConversationEntity.builder().account(customer).build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private void echoSavedMessage() {
        when(messages.save(any(ChatMessageEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ──────────────── mo hoi thoai ────────────────

    @Test
    void firstVisitCreatesTheConversationSoFrontEndNeedsOneCallOnly() {
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.empty());
        when(accounts.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(conversations.saveAndFlush(any(ConversationEntity.class)))
                .thenAnswer(call -> call.getArgument(0));

        var result = service.getOrCreateMyConversation(customer.getId());

        assertEquals(0, result.unreadCount());
        assertNull(result.lastMessagePreview());
        verify(conversations).saveAndFlush(any(ConversationEntity.class));
    }

    @Test
    void secondVisitReusesTheSameConversation() {
        ConversationEntity existing = conversation();
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.of(existing));

        assertEquals(existing.getId(), service.getOrCreateMyConversation(customer.getId()).id());
        verify(conversations, never()).saveAndFlush(any());
    }

    /** Hai request vao cung luc: rang buoc unique chan ban thu hai, phai doc lai chu khong hong. */
    @Test
    void concurrentFirstVisitFallsBackToTheRowTheOtherRequestInserted() {
        ConversationEntity winner = conversation();
        when(conversations.findByAccountId(customer.getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(accounts.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(conversations.saveAndFlush(any(ConversationEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_conversations_account"));

        assertEquals(winner.getId(), service.getOrCreateMyConversation(customer.getId()).id());
    }

    // ──────────────── gui tin ────────────────

    @Test
    void customerMessageBumpsAdminUnreadAndClearsItsOwn() {
        ConversationEntity entity = conversation();
        entity.setUnreadByCustomer(4);
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.of(entity));
        echoSavedMessage();

        var result = service.sendAsCustomer(customer.getId(),
                new ChatMessageRequest("Ao nay con size M khong shop?"));

        assertEquals(SenderType.CUSTOMER, result.senderType());
        assertEquals(1, entity.getUnreadByAdmin());
        assertEquals(0, entity.getUnreadByCustomer());   // minh vua go thi coi nhu da doc
        assertEquals("Ao nay con size M khong shop?", entity.getLastMessagePreview());
        assertTrue(entity.getLastMessageAt() != null);
    }

    @Test
    void adminReplyBumpsCustomerUnreadAndClearsItsOwn() {
        ConversationEntity entity = conversation();
        entity.setUnreadByAdmin(2);
        AccountEntity admin = new AccountEntity();
        admin.setId(UUID.randomUUID());
        when(conversations.findWithAccountById(entity.getId())).thenReturn(Optional.of(entity));
        when(accounts.findById(admin.getId())).thenReturn(Optional.of(admin));
        echoSavedMessage();

        var result = service.sendAsAdmin(admin.getId(), entity.getId(),
                new ChatMessageRequest("Con size M ban nhe"));

        assertEquals(SenderType.ADMIN, result.senderType());
        assertEquals(1, entity.getUnreadByCustomer());
        assertEquals(0, entity.getUnreadByAdmin());
    }

    @Test
    void previewIsCutToTwoHundredCharactersWithoutBreaking() {
        ConversationEntity entity = conversation();
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.of(entity));
        echoSavedMessage();

        service.sendAsCustomer(customer.getId(), new ChatMessageRequest("x".repeat(500)));

        assertEquals(200, entity.getLastMessagePreview().length());
    }

    @Test
    void messageContentIsTrimmedBeforeStoring() {
        ConversationEntity entity = conversation();
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.of(entity));
        echoSavedMessage();

        var result = service.sendAsCustomer(customer.getId(), new ChatMessageRequest("   chao shop   "));

        assertEquals("chao shop", result.content());
    }

    @Test
    void adminCannotReplyToAConversationThatDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(conversations.findWithAccountById(missing)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.sendAsAdmin(UUID.randomUUID(), missing, new ChatMessageRequest("hi")));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    // ──────────────── doc tin ────────────────

    @Test
    void pollingWithSinceOnlyReturnsNewerMessages() {
        ConversationEntity entity = conversation();
        Instant since = Instant.parse("2026-10-17T09:30:00Z");
        ChatMessageEntity newer = ChatMessageEntity.builder()
                .conversation(entity).senderType(SenderType.ADMIN).content("moi").build();
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.of(entity));
        when(messages.findByConversationIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
                entity.getId(), since)).thenReturn(List.of(newer));

        var result = service.getMyMessagesSince(customer.getId(), since);

        assertEquals(1, result.size());
        assertEquals("moi", result.get(0).content());
    }

    @Test
    void pollingBeforeTheCustomerEverWroteReturnsEmptyInsteadOfFailing() {
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.empty());

        assertTrue(service.getMyMessagesSince(customer.getId(), Instant.now()).isEmpty());
        verifyNoMessageLookup();
    }

    private void verifyNoMessageLookup() {
        verify(messages, never())
                .findByConversationIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void historyIsPagedNewestFirst() {
        ConversationEntity entity = conversation();
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.of(entity));
        when(messages.findByConversationIdOrderByCreatedAtDesc(entity.getId(), page))
                .thenReturn(new PageImpl<>(List.of(ChatMessageEntity.builder()
                        .conversation(entity).senderType(SenderType.CUSTOMER).content("a").build())));

        assertEquals(1, service.getMyMessageHistory(customer.getId(), page).content().size());
    }

    // ──────────────── danh sach admin ────────────────

    @Test
    void adminListJoinsCustomerNameInOneBatchNotPerRow() {
        ConversationEntity entity = conversation();
        entity.setUnreadByAdmin(3);
        ProfileEntity profile = ProfileEntity.builder().account(customer).fullName("Nguyen Van A").build();
        when(conversations.findByLastMessageAtNotNullOrderByLastMessageAtDesc(page))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(profiles.findByAccountIdIn(anyCollection())).thenReturn(List.of(profile));

        var result = service.getAdminConversations(page);

        assertEquals(1, result.content().size());
        assertEquals("Nguyen Van A", result.content().get(0).customerName());
        assertEquals("khach@example.com", result.content().get(0).customerEmail());
        assertEquals(3, result.content().get(0).unreadByAdmin());
        // Mot lan tra ho so cho ca trang, khong phai moi dong mot lan.
        verify(profiles).findByAccountIdIn(anyCollection());
    }

    @Test
    void adminListSurvivesACustomerWithoutAProfile() {
        when(conversations.findByLastMessageAtNotNullOrderByLastMessageAtDesc(page))
                .thenReturn(new PageImpl<>(List.of(conversation())));
        when(profiles.findByAccountIdIn(anyCollection())).thenReturn(List.of());

        assertNull(service.getAdminConversations(page).content().get(0).customerName());
    }

    /**
     * Hoi thoai 0 tin KHONG duoc xuat hien o danh sach admin.
     *
     * <p>GET /conversations/me tao san hoi thoai ngay lan khach mo man chat, nen co nhung cuoc
     * rong voi lastMessageAt = null. Postgres coi NULL la lon nhat nen DESC se day chung len
     * dau, chan het cac cuoc dang cho tra loi.</p>
     */
    @Test
    void adminListAsksOnlyForConversationsThatAlreadyHaveAMessage() {
        when(conversations.findByLastMessageAtNotNullOrderByLastMessageAtDesc(page))
                .thenReturn(new PageImpl<>(List.of()));

        service.getAdminConversations(page);

        verify(conversations).findByLastMessageAtNotNullOrderByLastMessageAtDesc(page);
        verify(conversations, never()).findAll(any(Pageable.class));
    }

    @Test
    void emptyAdminListSkipsTheProfileQueryEntirely() {
        when(conversations.findByLastMessageAtNotNullOrderByLastMessageAtDesc(page))
                .thenReturn(new PageImpl<>(List.of()));

        assertTrue(service.getAdminConversations(page).content().isEmpty());
        verify(profiles, never()).findByAccountIdIn(anyCollection());
    }

    // ──────────────── danh dau da doc ────────────────

    @Test
    void customerReadClearsOnlyItsOwnCounter() {
        ConversationEntity entity = conversation();
        entity.setUnreadByCustomer(5);
        entity.setUnreadByAdmin(2);
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.of(entity));
        when(conversations.save(entity)).thenReturn(entity);

        assertEquals(0, service.markReadByCustomer(customer.getId()).unreadCount());
        assertEquals(2, entity.getUnreadByAdmin());   // phia kia khong bi dung toi
    }

    @Test
    void adminReadClearsOnlyItsOwnCounter() {
        ConversationEntity entity = conversation();
        entity.setUnreadByAdmin(7);
        entity.setUnreadByCustomer(1);
        when(conversations.findWithAccountById(entity.getId())).thenReturn(Optional.of(entity));
        when(profiles.findByAccountId(customer.getId())).thenReturn(Optional.empty());

        assertEquals(0, service.markReadByAdmin(entity.getId()).unreadByAdmin());
        assertEquals(1, entity.getUnreadByCustomer());
    }

    @Test
    void adminBadgeCountsConversationsWaitingForAReply() {
        when(conversations.countByUnreadByAdminGreaterThan(0)).thenReturn(3L);

        assertEquals(3L, service.countConversationsAwaitingAdmin());
    }

    @Test
    void conversationIsAlwaysBoundToTheCallerAccount() {
        when(conversations.findByAccountId(customer.getId())).thenReturn(Optional.empty());
        when(accounts.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(conversations.saveAndFlush(any(ConversationEntity.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.getOrCreateMyConversation(customer.getId());

        verify(conversations).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                saved -> assertSameAccount(saved)));
    }

    private boolean assertSameAccount(ConversationEntity saved) {
        assertSame(customer, saved.getAccount());
        return true;
    }
}
