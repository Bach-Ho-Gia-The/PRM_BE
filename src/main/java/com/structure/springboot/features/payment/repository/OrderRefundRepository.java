package com.structure.springboot.features.payment.repository;

import com.structure.springboot.features.payment.entity.OrderRefundEntity;
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
 * <p><b>Chỉ khai đúng hai hàm đợt này thật sự gọi tới.</b> Endpoint admin đọc danh sách khoản hoàn
 * thuộc đợt sau — lúc đó mới thêm hàm cho nó. Khai sẵn bây giờ là tạo ra method không có đường
 * chạy, đúng loại lỗi đã gặp với ba trạng thái payment không ai sinh ra được.</p>
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
}
