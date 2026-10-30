package com.structure.springboot.features.payment.repository;

import com.structure.springboot.features.payment.dto.projection.RefundAdminView;
import com.structure.springboot.features.payment.entity.OrderRefundEntity;
import com.structure.springboot.features.payment.enums.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Truy cập bảng {@code order_refunds}.
 *
 * <p><b>Chỉ derived query, không {@code @Query}</b>: mọi truy vấn cần dùng đều là một điều kiện
 * đơn giản trên một bảng. Thêm JPQL viết tay ở đây chỉ làm khó người đọc mà không nhanh hơn.</p>
 *
 * <p>Hai hàm đầu phục vụ luồng ghi hoàn tiền (CK-6). Hai hàm trả {@link RefundAdminView} phục vụ
 * màn danh sách của shop (CK-7a) — đọc bằng projection đóng để một câu SQL có sẵn {@code join
 * orders}, tránh N+1 khi hiển thị số đơn và tên khách.</p>
 */
@Repository
public interface OrderRefundRepository extends JpaRepository<OrderRefundEntity, UUID> {

    /**
     * Khoản hoàn của một đơn.
     *
     * <p>Trả {@code Optional} chứ không {@code List} vì {@code order_id} có ràng buộc unique —
     * kiểu trả về nói đúng điều mà ràng buộc dữ liệu đảm bảo. Người đọc thấy {@code Optional} là
     * biết không cần lo trường hợp nhiều dòng.</p>
     */
    Optional<OrderRefundEntity> findByOrderId(UUID orderId);

    /**
     * Đơn này đã có khoản hoàn chưa.
     *
     * <p>Đây là hàm <b>các đường ghi phải gọi trước</b> để giữ tính idempotent: nhiều đường cùng
     * có thể phát sinh hoàn tiền cho một đơn (khách huỷ đơn đã trả tiền, shop huỷ, IPN báo về
     * muộn, IPN gửi lặp), và lần gọi thứ hai phải im lặng bỏ qua chứ không ném. Kiểm bằng
     * {@code exists} thay vì tải cả bản ghi lên — chỉ cần biết có hay không.</p>
     */
    boolean existsByOrderId(UUID orderId);

    /**
     * Danh sách khoản hoàn <b>theo một trạng thái</b>, cho màn quản trị khi shop lọc
     * {@code ?status=}.
     *
     * <p>Trả projection {@link RefundAdminView} nên câu SQL chỉ {@code select} vài cột và
     * {@code join orders} đúng một lần. Thứ tự do {@link Pageable} quyết định (controller mặc định
     * {@code createdAt DESC} — mới nhất trước).</p>
     */
    Page<RefundAdminView> findByStatus(RefundStatus status, Pageable pageable);

    /**
     * <b>Tất cả</b> khoản hoàn, khi shop không lọc trạng thái.
     *
     * <p><b>Vì sao {@code findByCreatedAtNotNull} chứ không {@code findAll}:</b> cần trả
     * {@link RefundAdminView} (projection) để tránh N+1, mà {@code JpaRepository.findAll(Pageable)}
     * chỉ trả về entity. Một derived query cần ít nhất một điều kiện; {@code createdAt} là
     * {@code @CreationTimestamp nullable=false} nên luôn có giá trị, khiến điều kiện "không null"
     * khớp đúng mọi dòng đã lưu — tức là "lấy tất cả" mà vẫn là derived query, không phải viết
     * {@code @Query}.</p>
     */
    Page<RefundAdminView> findByCreatedAtNotNull(Pageable pageable);
}
