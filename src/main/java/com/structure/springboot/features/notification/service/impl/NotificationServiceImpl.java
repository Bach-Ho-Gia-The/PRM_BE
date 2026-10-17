package com.structure.springboot.features.notification.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.notification.dto.response.NotificationResponse;
import com.structure.springboot.features.notification.entity.NotificationEntity;
import com.structure.springboot.features.notification.enums.NotificationType;
import com.structure.springboot.features.notification.mapper.NotificationMapper;
import com.structure.springboot.features.notification.repository.NotificationRepository;
import com.structure.springboot.features.notification.service.NotificationService;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /**
     * Câu chữ theo từng bước của đơn. Không có {@code PENDING}: khách vừa bấm đặt xong đang nhìn
     * màn kết quả, bắn thông báo ngay lúc đó là thừa.
     */
    private static final Map<OrderStatus, String[]> ORDER_MESSAGES = Map.of(
            OrderStatus.CONFIRMED, new String[]{"Đơn hàng đã được xác nhận",
                    "Đơn %s đã được xác nhận và đang được chuẩn bị."},
            OrderStatus.SHIPPING, new String[]{"Đơn hàng đang giao",
                    "Đơn %s đã được giao cho đơn vị vận chuyển."},
            OrderStatus.COMPLETED, new String[]{"Giao hàng thành công",
                    "Đơn %s đã giao thành công. Cảm ơn bạn!"},
            OrderStatus.CANCELLED, new String[]{"Đơn hàng đã huỷ",
                    "Đơn %s đã được huỷ."}
    );

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(UUID accountId, boolean unreadOnly,
                                                                 Pageable pageable) {
        // Thứ tự CỐ ĐỊNH mới nhất trước: màn thông báo chỉ có một thứ tự hợp lý, không nhận sort
        // của client (cùng cách trang bộ sưu tập công khai đang làm).
        var page = unreadOnly
                ? notificationRepository.findByAccountIdAndIsReadFalseOrderByCreatedAtDesc(accountId, pageable)
                : notificationRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
        return PageResponse.from(page.map(notificationMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public long countMyUnread(UUID accountId) {
        return notificationRepository.countByAccountIdAndIsReadFalse(accountId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID accountId, UUID notificationId) {
        NotificationEntity notification = requireMine(accountId, notificationId);
        // Gọi lại trên thông báo đã đọc vẫn trả 200: FE gọi lại thoải mái, không cần nhớ trạng thái.
        notification.setRead(true);
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public int markAllRead(UUID accountId) {
        return notificationRepository.markAllRead(accountId);
    }

    @Override
    @Transactional
    public void deleteMine(UUID accountId, UUID notificationId) {
        if (notificationRepository.deleteByIdAndAccountId(notificationId, accountId) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Notification not found with id: " + notificationId);
        }
    }

    @Override
    @Transactional
    public void notifyOrderStatusChanged(OrderEntity order) {
        String[] message = ORDER_MESSAGES.get(order.getStatus());
        if (message == null) {
            return; // PENDING hoặc trạng thái chưa có câu chữ: không bắn gì.
        }
        notificationRepository.save(NotificationEntity.builder()
                .account(order.getAccount())
                .type(NotificationType.ORDER_STATUS)
                .title(message[0])
                .content(String.format(message[1], order.getOrderNumber()))
                .referenceId(order.getId())
                .build());
    }

    /** Trả 404 chứ không 403: không để lộ rằng thông báo đó tồn tại và thuộc về người khác. */
    private NotificationEntity requireMine(UUID accountId, UUID notificationId) {
        return notificationRepository.findByIdAndAccountId(notificationId, accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Notification not found with id: " + notificationId));
    }
}
