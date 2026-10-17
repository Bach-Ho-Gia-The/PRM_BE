package com.structure.springboot.features.notification.repository;

import com.structure.springboot.features.notification.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    Page<NotificationEntity> findByAccountIdAndIsReadFalseOrderByCreatedAtDesc(UUID accountId,
                                                                               Pageable pageable);

    long countByAccountIdAndIsReadFalse(UUID accountId);

    /** Tra kèm accountId để không ai đọc/sửa được thông báo của người khác. */
    Optional<NotificationEntity> findByIdAndAccountId(UUID id, UUID accountId);

    /**
     * Đánh dấu đã đọc hàng loạt.
     *
     * <p>Dùng {@code @Query} vì derived query không làm được UPDATE. Tải từng bản ghi lên rồi sửa
     * là N câu SQL cho một thao tác vốn chỉ cần một câu.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE NotificationEntity n SET n.isRead = true "
            + "WHERE n.account.id = :accountId AND n.isRead = false")
    int markAllRead(@Param("accountId") UUID accountId);

    int deleteByIdAndAccountId(UUID id, UUID accountId);
}
