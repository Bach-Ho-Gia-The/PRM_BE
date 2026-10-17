package com.structure.springboot.features.chat.repository;

import com.structure.springboot.features.chat.entity.ChatMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    /** Lịch sử: mới nhất trước, có phân trang để cuộn ngược lên. */
    Page<ChatMessageEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId,
                                                                     Pageable pageable);

    /**
     * Chế độ hỏi lại theo chu kỳ: chỉ tin mới hơn mốc, sắp tăng dần, không phân trang.
     *
     * <p>Đây là thứ làm polling rẻ — không có tin mới thì trả danh sách rỗng.</p>
     */
    List<ChatMessageEntity> findByConversationIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
            UUID conversationId, Instant since);
}
