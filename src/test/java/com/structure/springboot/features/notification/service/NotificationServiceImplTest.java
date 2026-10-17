package com.structure.springboot.features.notification.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.notification.entity.NotificationEntity;
import com.structure.springboot.features.notification.enums.NotificationType;
import com.structure.springboot.features.notification.mapper.NotificationMapper;
import com.structure.springboot.features.notification.repository.NotificationRepository;
import com.structure.springboot.features.notification.service.impl.NotificationServiceImpl;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nhãn test để ASCII thuần vì output surefire mã hoá hỏng tiếng Việt có dấu. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notifications;
    private NotificationServiceImpl service;

    private final UUID me = UUID.randomUUID();
    private final Pageable page = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        NotificationMapper mapper = Mappers.getMapper(NotificationMapper.class);
        service = new NotificationServiceImpl(notifications, mapper);
    }

    private NotificationEntity notification(boolean read) {
        NotificationEntity entity = NotificationEntity.builder()
                .type(NotificationType.ORDER_STATUS).title("T").content("C").isRead(read).build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private OrderEntity order(OrderStatus status) {
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        OrderEntity entity = OrderEntity.builder()
                .orderNumber("LBVN-2610-A7K2").account(account).status(status).build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    // ──────────────── sinh thong bao tu don hang ────────────────

    @Test
    void confirmedOrderCreatesNotificationCarryingOrderIdAsReference() {
        OrderEntity entity = order(OrderStatus.CONFIRMED);

        service.notifyOrderStatusChanged(entity);

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(saved.capture());
        assertEquals(NotificationType.ORDER_STATUS, saved.getValue().getType());
        assertEquals(entity.getId(), saved.getValue().getReferenceId());
        assertSame(entity.getAccount(), saved.getValue().getAccount());
        assertTrue(saved.getValue().getContent().contains("LBVN-2610-A7K2"));
    }

    @Test
    void pendingOrderCreatesNothingBecauseCustomerIsStillLookingAtTheScreen() {
        service.notifyOrderStatusChanged(order(OrderStatus.PENDING));

        verify(notifications, never()).save(any());
    }

    @Test
    void everyTerminalStatusHasItsOwnWording() {
        for (OrderStatus status : List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPING,
                OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
            service.notifyOrderStatusChanged(order(status));
        }

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications, org.mockito.Mockito.times(4)).save(saved.capture());
        assertEquals(4, saved.getAllValues().stream().map(NotificationEntity::getTitle).distinct().count());
    }

    /**
     * Thong bao ghi CHUNG giao dich voi viec doi trang thai don.
     *
     * <p>Ban dau co try/catch voi y "thong bao hong thi don van doi trang thai". Cach do khong
     * chay: save() chua day cau lenh xuong DB nen loi rang buoc chi no luc commit giao dich
     * ngoai, tuc la sau khi catch da thoat. Nay de loi noi len de hai thu luon khop nhau.</p>
     */
    @Test
    void notificationFailurePropagatesSoOrderAndInboxNeverDiverge() {
        when(notifications.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThrows(RuntimeException.class,
                () -> service.notifyOrderStatusChanged(order(OrderStatus.SHIPPING)));
    }

    // ──────────────── doc / danh dau ────────────────

    @Test
    void listUsesUnreadQueryOnlyWhenAsked() {
        when(notifications.findByAccountIdAndIsReadFalseOrderByCreatedAtDesc(me, page))
                .thenReturn(new PageImpl<>(List.of(notification(false))));

        var result = service.getMyNotifications(me, true, page);

        assertEquals(1, result.content().size());
        verify(notifications, never()).findByAccountIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listDefaultsToEverything() {
        when(notifications.findByAccountIdOrderByCreatedAtDesc(me, page))
                .thenReturn(new PageImpl<>(List.of(notification(false), notification(true))));

        assertEquals(2, service.getMyNotifications(me, false, page).content().size());
    }

    @Test
    void unreadCountIsScopedToTheCaller() {
        when(notifications.countByAccountIdAndIsReadFalse(me)).thenReturn(3L);

        assertEquals(3L, service.countMyUnread(me));
    }

    @Test
    void markReadFlipsTheFlag() {
        NotificationEntity entity = notification(false);
        when(notifications.findByIdAndAccountId(entity.getId(), me)).thenReturn(Optional.of(entity));
        when(notifications.save(entity)).thenReturn(entity);

        var result = service.markRead(me, entity.getId());

        assertTrue(result.isRead());
    }

    @Test
    void markReadIsIdempotent() {
        NotificationEntity entity = notification(true);
        when(notifications.findByIdAndAccountId(entity.getId(), me)).thenReturn(Optional.of(entity));
        when(notifications.save(entity)).thenReturn(entity);

        assertTrue(service.markRead(me, entity.getId()).isRead());
    }

    /** Thong bao cua nguoi khac phai la 404 chu khong 403: khong lo rang no ton tai. */
    @Test
    void markReadOnSomeoneElsesNotificationIsNotFoundNotForbidden() {
        UUID id = UUID.randomUUID();
        when(notifications.findByIdAndAccountId(id, me)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.markRead(me, id));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void markAllReadReturnsHowManyChanged() {
        when(notifications.markAllRead(me)).thenReturn(5);

        assertEquals(5, service.markAllRead(me));
    }

    @Test
    void markAllReadOnAnAlreadyCleanInboxReturnsZeroWithoutError() {
        when(notifications.markAllRead(me)).thenReturn(0);

        assertEquals(0, service.markAllRead(me));
    }

    @Test
    void deleteRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(notifications.deleteByIdAndAccountId(id, me)).thenReturn(0);

        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.deleteMine(me, id)).getErrorCode());
    }

    @Test
    void deleteSucceedsWhenRowBelongsToCaller() {
        UUID id = UUID.randomUUID();
        when(notifications.deleteByIdAndAccountId(id, me)).thenReturn(1);

        assertDoesNotThrow(() -> service.deleteMine(me, id));
    }
}
