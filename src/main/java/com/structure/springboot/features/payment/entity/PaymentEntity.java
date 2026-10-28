package com.structure.springboot.features.payment.entity;

import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_txn_ref",
                columnNames = "txn_ref"
        ),
        indexes = {
                @Index(
                        name = "idx_payments_order_id",
                        columnList = "order_id"
                ),
                @Index(
                        name = "idx_payments_status_expires_at",
                        columnList = "status, expires_at"
                )
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private OrderEntity order;

    /** Mã gửi sang VNPay bằng tham số vnp_TxnRef. */
    @Column(name = "txn_ref", nullable = false, updatable = false, length = 100)
    private String txnRef;

    /** Số tiền VND gốc; chưa nhân 100 như dữ liệu gửi VNPay. */
    @Column(
            name = "amount",
            nullable = false,
            updatable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Thời điểm thanh toán thành công theo dữ liệu đã xác thực. */
    @Column(name = "paid_at")
    private Instant paidAt;

    /** vnp_TransactionNo do VNPay trả về. */
    @Column(name = "gateway_transaction_no", length = 50)
    private String gatewayTransactionNo;

    /** vnp_ResponseCode do VNPay trả về. */
    @Column(name = "response_code", length = 2)
    private String responseCode;

    /** vnp_TransactionStatus do VNPay trả về. */
    @Column(name = "gateway_transaction_status", length = 2)
    private String gatewayTransactionStatus;

    @Column(name = "bank_code", length = 50)
    private String bankCode;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}