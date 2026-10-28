package com.structure.springboot.features.order.repository;

import com.structure.springboot.features.order.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID>,
        JpaSpecificationExecutor<OrderEntity> {

    /**
     * Đơn kèm dòng hàng, dành cho màn chi tiết của admin và cho việc hoàn kho khi huỷ.
     *
     * <p>Không có {@code @EntityGraph} thì mỗi lần đụng {@code order.getItems()} lại thêm một câu
     * SQL, mà hoàn kho thì bắt buộc phải duyệt hết dòng hàng.</p>
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findWithItemsById(UUID id);

    /** Bản tra theo mã đơn cho màn quản trị; cùng @EntityGraph nên cũng không N+1. */
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findWithItemsByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * Tra đơn theo mã NHƯNG buộc phải khớp chủ sở hữu.
     *
     * <p>Lọc ngay trong câu SQL thay vì tải đơn lên rồi mới so {@code accountId} trong Java:
     * quên một dòng so sánh ở service là lộ đơn hàng người khác. Ở đây muốn quên cũng không được.</p>
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findByOrderNumberAndAccountId(String orderNumber, UUID accountId);

    /**
     * Đơn kèm dòng hàng, <b>khoá bi quan trên chính dòng đơn</b> — dùng khi sắp đổi trạng thái.
     *
     * <p><b>Đây là chốt chặn thứ nhất chống hoàn kho hai lần.</b> Khách huỷ, shop huỷ, tác vụ quét
     * hết hạn và IPN có thể cùng nhắm một đơn. Đường tới trước giữ khoá và ghi {@code CANCELLED};
     * đường tới sau <b>chờ</b> cho giao dịch đầu kết thúc, đọc lại thấy trạng thái đã đổi nên bị
     * chặn — thay vì cả hai cùng đọc ra {@code PENDING} rồi cùng cộng kho.</p>
     *
     * <p>Khoá trên <b>đơn</b> chứ không phải trên tồn kho là có chủ đích: câu hỏi ở đây là "đơn này
     * đã được xử lý chưa", không phải "dòng kho này có bị ai sửa không". Việc khoá tồn kho thuộc
     * {@code ProductVariantRepository.findByProductIdInOrderByIdAsc}.</p>
     *
     * <p>Khoá <b>tự nhả</b> khi giao dịch kết thúc (commit hoặc rollback) — không có thao tác
     * "trả khoá" nào trong code.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findWithItemsByIdForUpdate(UUID id);

    /**
     * Bản khoá theo <b>mã đơn của chính chủ sở hữu</b> — dùng cho luồng khách tự huỷ.
     *
     * <p>Lọc luôn {@code accountId} ngay trong câu SQL: quên một dòng so sánh ở service là huỷ được
     * đơn của người khác. Ở đây muốn quên cũng không được.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findByOrderNumberAndAccountIdForUpdate(String orderNumber, UUID accountId);

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
