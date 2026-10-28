package com.structure.springboot.features.payment.repository;

import com.structure.springboot.features.payment.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Truy cập bảng {@code payments}.
 *
 * <p><b>Vì sao không còn method khoá đơn ở đây:</b> bản nháp trước có
 * {@code findForPaymentByOrderNumberAndAccountId} trả {@code Optional<OrderEntity>} — sai kiểu
 * (repository này quản lý {@code PaymentEntity}), lại nằm sai module (tra đơn hàng là việc của
 * {@code OrderRepository}), và gắn {@code @Lock} lên một derived query không có {@code @Query}
 * nên không diễn đạt được ý định. Không nơi nào gọi. Khi cần khoá đơn để xử lý thanh toán, method
 * tương ứng sẽ được khai trong {@code OrderRepository} — đúng chủ sở hữu của bảng {@code orders}.
 */
@Repository
public interface PaymentRepository
        extends JpaRepository<PaymentEntity, UUID> {

    /**
     * Một lần thử thanh toán là duy nhất theo {@code txnRef} — đây cũng là khoá mà VNPay gửi ngược
     * về trong IPN, nên tra theo nó là đường vào của mọi kết quả thanh toán.
     */
    Optional<PaymentEntity> findByTxnRef(String txnRef);

    /** Lần thử thanh toán gần nhất của một đơn. Dùng cho màn hình trạng thái thanh toán. */
    Optional<PaymentEntity> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
