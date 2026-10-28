package com.structure.springboot.features.payment.repository;

import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Truy cập bảng {@code payments}.
 *
 * <p><b>Vì sao không có method khoá đơn ở đây:</b> bản nháp trước có
 * {@code findForPaymentByOrderNumberAndAccountId} trả {@code Optional<OrderEntity>} — sai kiểu
 * (repository này quản lý {@code PaymentEntity}), lại nằm sai module (tra đơn hàng là việc của
 * {@code OrderRepository}), và gắn {@code @Lock} lên một derived query không có {@code @Query}
 * nên không diễn đạt được ý định. Khoá đơn để xử lý thanh toán khai trong {@code OrderRepository}
 * — đúng chủ sở hữu của bảng {@code orders}.</p>
 */
@Repository
public interface PaymentRepository
        extends JpaRepository<PaymentEntity, UUID> {

    /**
     * Một lần thử thanh toán là duy nhất theo {@code txnRef} — đây cũng là khoá mà VNPay gửi ngược
     * về trong IPN, nên tra theo nó là đường vào của mọi kết quả thanh toán.
     */
    Optional<PaymentEntity> findByTxnRef(String txnRef);

    /**
     * Lần thử thanh toán gần nhất của một đơn.
     *
     * <p>Sắp theo {@code attemptNo} chứ <b>không</b> theo {@code createdAt}: hai lần thử tạo trong
     * cùng một giây thì {@code createdAt} bằng nhau và thứ tự trả về là tuỳ database.
     * {@code attemptNo} là số đếm có ràng buộc unique nên luôn xác định.</p>
     */
    Optional<PaymentEntity> findFirstByOrderIdOrderByAttemptNoDesc(UUID orderId);

    /** Đơn này đã có bao nhiêu lần thử thanh toán — dùng để cấp số cho lần kế tiếp. */
    int countByOrderId(UUID orderId);

    /**
     * Đơn này đã có lần thử nào ở trạng thái đó chưa.
     *
     * <p>Dùng để trả lời "đơn đã thanh toán chưa" mà không phải tải cả danh sách lên.</p>
     */
    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}
