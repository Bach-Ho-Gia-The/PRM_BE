package com.structure.springboot.features.order.repository;

import com.structure.springboot.features.order.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    boolean existsByOrderNumber(String orderNumber);

    /**
     * Tra đơn theo mã NHƯNG buộc phải khớp chủ sở hữu.
     *
     * <p>Lọc ngay trong câu SQL thay vì tải đơn lên rồi mới so {@code accountId} trong Java:
     * quên một dòng so sánh ở service là lộ đơn hàng người khác. Ở đây muốn quên cũng không được.</p>
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findByOrderNumberAndAccountId(String orderNumber, UUID accountId);

    /** Danh sách đơn của tôi. KHÔNG fetch items — màn hình danh sách không dùng tới. */
    Page<OrderEntity> findByAccountId(UUID accountId, Pageable pageable);

    /**
     * Tài khoản này đã từng đặt đơn nào chưa.
     *
     * <p>Dùng {@code exists} chứ không đếm hay tải danh sách: chỉ cần biết có hay không, để
     * phía tài khoản chặn lệnh xoá trước khi database kịp ném lỗi khoá ngoại.</p>
     */
    boolean existsByAccountId(UUID accountId);
}
