package com.structure.springboot.features.notification.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.notification.dto.response.NotificationResponse;
import com.structure.springboot.features.order.entity.OrderEntity;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    PageResponse<NotificationResponse> getMyNotifications(UUID accountId, boolean unreadOnly,
                                                          Pageable pageable);

    long countMyUnread(UUID accountId);

    NotificationResponse markRead(UUID accountId, UUID notificationId);

    /** @return số thông báo vừa chuyển sang đã đọc */
    int markAllRead(UUID accountId);

    void deleteMine(UUID accountId, UUID notificationId);

    /**
     * Sinh thông báo khi đơn đổi trạng thái. Gọi từ trong giao dịch của bên đổi trạng thái.
     *
     * <p><b>CÙNG GIAO DỊCH, CỐ Ý KHÔNG NUỐT LỖI.</b> Bản đầu có {@code try/catch} với ý "thông báo
     * hỏng thì đơn vẫn đổi trạng thái", nhưng cách đó <b>không chạy</b>: {@code save()} với khoá
     * UUID chưa đẩy câu lệnh xuống database ngay, nên vi phạm ràng buộc chỉ nổ lúc <b>commit giao
     * dịch ngoài</b> — tức là sau khi khối {@code catch} đã thoát từ lâu. Bắt rồi đi tiếp trong
     * cùng giao dịch còn tệ hơn: Hibernate đã đánh dấu rollback-only nên commit sẽ ném
     * {@code UnexpectedRollbackException}.</p>
     *
     * <p>Nuốt lỗi chỉ hợp lý với hệ thống bên ngoài (Cloudinary, Brevo). Đây là một dòng INSERT
     * xuống chính database đang mở, dữ liệu có biên rõ ràng và tài khoản chắc chắn tồn tại — ghi
     * chung giao dịch để thông báo và trạng thái đơn luôn khớp nhau, không bao giờ lệch.</p>
     */
    void notifyOrderStatusChanged(OrderEntity order);
}
