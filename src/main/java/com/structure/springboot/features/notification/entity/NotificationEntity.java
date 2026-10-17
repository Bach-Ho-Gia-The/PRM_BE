package com.structure.springboot.features.notification.entity;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.notification.enums.NotificationType;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Một dòng thông báo thuộc về đúng một tài khoản.
 *
 * <p>Kế thừa {@code BaseEntity} chứ không {@code SoftDeletableEntity}: thông báo không có khái
 * niệm ẩn/hiện, xoá là xoá hẳn.</p>
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                // Màn thông báo luôn lọc theo chủ nhân rồi sắp mới nhất trước.
                @Index(name = "idx_notifications_account", columnList = "account_id, created_at"),
                // Badge đếm chưa đọc chạy rất thường xuyên nên tách index riêng.
                @Index(name = "idx_notifications_unread", columnList = "account_id, is_read")
        }
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    /** Id của thứ được nhắc tới (hiện là đơn hàng) để FE bấm vào là nhảy thẳng tới màn chi tiết. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;
}
