package com.structure.springboot.features.chat.repository;

import com.structure.springboot.features.chat.entity.ConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByAccountId(UUID accountId);

    /**
     * Danh sách hội thoại cho admin: chỉ cuộc ĐÃ CÓ TIN, mới nhất lên đầu.
     *
     * <p><b>Vì sao phải lọc {@code LastMessageAtNotNull}:</b> {@code GET /conversations/me} tạo
     * sẵn hội thoại ngay lần khách mở màn chat, nên có những cuộc 0 tin nhắn với
     * {@code last_message_at = null}. PostgreSQL coi NULL là lớn nhất, vậy nên {@code DESC} sẽ
     * đẩy đám rỗng đó LÊN ĐẦU, chắn hết các cuộc đang chờ trả lời. Lọc bỏ luôn vừa khỏi lệ thuộc
     * vào quy ước NULL của từng loại database, vừa đúng nghiệp vụ: cuộc chưa có tin nào thì admin
     * không có gì để đọc cũng không có gì để trả lời.</p>
     *
     * <p>{@code @EntityGraph} nạp luôn {@code account} để lấy email khách mà không sinh N+1.</p>
     */
    @EntityGraph(attributePaths = {"account"})
    Page<ConversationEntity> findByLastMessageAtNotNullOrderByLastMessageAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"account"})
    Optional<ConversationEntity> findWithAccountById(UUID id);

    long countByUnreadByAdminGreaterThan(int threshold);
}
