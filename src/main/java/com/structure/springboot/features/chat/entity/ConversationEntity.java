package com.structure.springboot.features.chat.entity;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Một luồng trò chuyện giữa một khách và shop.
 *
 * <p>Ràng buộc {@code unique} trên {@code account_id} thể hiện lựa chọn nghiệp vụ: mỗi khách đúng
 * một hội thoại, giống Zalo OA. Nó cũng là thứ chặn việc tạo trùng khi hai request vào cùng lúc.</p>
 *
 * <p><b>Ba field lặp dữ liệu có chủ đích</b> ({@code lastMessageAt}, {@code lastMessagePreview},
 * {@code unreadByAdmin}/{@code unreadByCustomer}): màn danh sách hội thoại của admin cần hiện tin
 * cuối và số chưa đọc cho mỗi dòng. Không có chúng thì mỗi dòng phải chạy thêm hai truy vấn phụ.
 * Đổi lại, <b>mọi đường ghi tin nhắn đều phải cập nhật chúng</b> — vì vậy service chỉ có đúng một
 * hàm gửi tin dùng chung cho cả hai phía.</p>
 */
@Entity
@Table(
        name = "conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversations_account",
                columnNames = "account_id"),
        indexes = @Index(name = "idx_conversations_last_message", columnList = "last_message_at")
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private AccountEntity account;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Builder.Default
    @Column(name = "unread_by_customer", nullable = false)
    private int unreadByCustomer = 0;

    @Builder.Default
    @Column(name = "unread_by_admin", nullable = false)
    private int unreadByAdmin = 0;
}
