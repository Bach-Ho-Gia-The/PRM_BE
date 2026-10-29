package com.structure.springboot.features.payment.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.payment.dto.request.VnpayPaymentRequest;
import com.structure.springboot.features.payment.dto.response.PaymentResponse;
import com.structure.springboot.features.payment.dto.response.VnpayPaymentUrlResponse;
import com.structure.springboot.features.payment.entity.OrderRefundEntity;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.enums.RefundStatus;
import com.structure.springboot.features.payment.mapper.PaymentMapper;
import com.structure.springboot.features.payment.repository.OrderRefundRepository;
import com.structure.springboot.features.payment.repository.PaymentRepository;
import com.structure.springboot.features.payment.service.PaymentService;
import com.structure.springboot.infrastructure.external.vnpay.VnpayPaymentUrlBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRefundRepository orderRefundRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    private final VnpayPaymentUrlBuilder vnpayPaymentUrlBuilder;

    @Override
    @Transactional
    public PaymentEntity createFirstAttempt(OrderEntity order) {
        return paymentRepository.save(PaymentEntity.builder()
                .order(order)
                .txnRef(newTxnRef())
                // Số tiền phải là tổng đơn khách thực trả, gồm cả phí ship — không phải subtotal.
                .amount(order.getTotal())
                .status(PaymentStatus.PENDING)
                // Sao chép hạn từ ĐƠN, không tự tính: mọi lần thử sau cũng phải dùng đúng hạn này.
                // COD thì order.paymentDueAt = null ⇒ payment cũng không có hạn.
                .expiresAt(order.getPaymentDueAt())
                .attemptNo(1)
                .build());
    }

    @Override
    @Transactional
    public VnpayPaymentUrlResponse startVnpayPayment(UUID accountId, String orderNumber, String clientIp) {
        // Khoá đơn: hai request bấm "thanh toán" cùng lúc không được cùng sinh ra một lần thử mới,
        // và không được cùng đọc ra số lần thử cũ rồi đội số của nhau.
        OrderEntity order = orderRepository.findForUpdateByOrderNumberAndAccountId(
                        normalizeOrderNumber(orderNumber), accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Order not found with number: " + orderNumber));

        requirePayableByVnpay(order);

        PaymentEntity payment = resolveAttemptFor(order);

        String paymentUrl = vnpayPaymentUrlBuilder.build(new VnpayPaymentRequest(
                payment.getTxnRef(),
                payment.getAmount(),
                payment.getCreatedAt(),
                payment.getExpiresAt(),
                clientIp
        ));

        return new VnpayPaymentUrlResponse(
                paymentUrl,
                payment.getTxnRef(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getExpiresAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getMyPayment(UUID accountId, String orderNumber) {
        // Tra đơn trước để dùng lại đúng câu lọc chủ sở hữu: khách không xem được thanh toán của
        // đơn người khác, và nhận 404 chứ không 403 (không để lộ đơn có tồn tại hay không).
        OrderEntity order = orderRepository.findByOrderNumberAndAccountId(
                        normalizeOrderNumber(orderNumber), accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Order not found with number: " + orderNumber));

        return paymentRepository.findFirstByOrderIdOrderByAttemptNoDesc(order.getId())
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "This order has no payment record: " + orderNumber));
    }

    @Override
    @Transactional
    public void releaseForOrderCancel(OrderEntity order) {
        closeOpenAttempt(order, PaymentStatus.CANCELLED);
    }

    @Override
    @Transactional
    public void expireForOrder(OrderEntity order) {
        closeOpenAttempt(order, PaymentStatus.EXPIRED);
    }

    @Override
    @Transactional(readOnly = true)
    public Instant findPaidAtForOrder(UUID orderId) {
        if (orderId == null) {
            return null;
        }
        return paymentRepository.findFirstByOrderIdOrderByAttemptNoDesc(orderId)
                .map(PaymentEntity::getPaidAt)
                .orElse(null);
    }

    @Override
    @Transactional
    public Optional<OrderEntity> findOrderForConfirmation(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        // Finder CÓ khoá, không phải findById — xem javadoc ở interface. Đừng "toi ưu" thành
        // findById: mất khoá là mở lại đúng khe tranh chấp mà hàm này sinh ra để bịt.
        return orderRepository.findForUpdateWithItemsById(orderId);
    }

    @Override
    @Transactional
    public void requestRefundIfPaid(OrderEntity order, RefundReason reason) {
        if (order == null || order.getId() == null) {
            return;
        }

        // Tìm ĐÚNG lần thử đã thu được tiền, không phải lần mới nhất: một đơn có thể có nhiều lần
        // thử và con trỏ hoàn tiền phải trỏ vào bản ghi thật sự có tiền vào.
        Optional<PaymentEntity> paidPayment = paymentRepository
                .findFirstByOrderIdOrderByAttemptNoDesc(order.getId())
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID);

        if (paidPayment.isEmpty()) {
            // Ca thường gặp nhất: huỷ đơn chưa thanh toán. Không có tiền vào thì không có gì để
            // hoàn, và đây KHÔNG phải lỗi.
            return;
        }

        // Kiểm trước rồi mới ghi: ràng buộc unique trên order_id là lớp chặn cuối, nhưng để nó
        // ném ra thì phải bắt exception và đoán xem có phải trùng hay không. Kiểm trước thì
        // trường hợp "sự kiện đến lần thứ hai" được xử lý bằng một câu if đọc là hiểu.
        if (orderRefundRepository.existsByOrderId(order.getId())) {
            return;
        }

        PaymentEntity payment = paidPayment.get();

        // amount lấy từ payments.amount chứ KHÔNG phải orders.total: đây là số tiền thực sự đã
        // thu. Xem javadoc OrderRefundEntity để biết vì sao hai con số này có thể lệch.
        orderRefundRepository.save(OrderRefundEntity.builder()
                .order(order)
                .payment(payment)
                .amount(payment.getAmount())
                .status(RefundStatus.PENDING)
                .reason(reason)
                .build());
    }

    /**
     * Khuôn chung cho hai đường đóng payment — <b>chỉ khác trạng thái đích</b>.
     *
     * <p>Tách ra vì hai hàm gọi nó phải hành xử y hệt nhau ở mọi nhánh còn lại. Nếu mỗi hàm tự
     * viết, sớm muộn sẽ có một bản quên nhánh "PAID thì không đụng" và ghi đè mất dấu vết một giao
     * dịch đã thu được tiền — loại lỗi không thể phát hiện bằng mắt trên dữ liệu.</p>
     *
     * <p><b>Vì sao chỉ xét payment mới nhất:</b> chỉ bản mới nhất mới có thể còn {@code PENDING}.
     * Các bản cũ đã dứt điểm theo đúng luật "chỉ sinh lần thử mới khi lần trước đã thất bại". Quét
     * toàn bộ là việc làm vô nghĩa và còn che mất dấu vết lịch sử.</p>
     *
     * <p>Không {@code save} khi không đổi gì: {@code @Transactional} cộng với entity đang được quản
     * lý thì thay đổi tự được ghi ra khi commit; gọi {@code save} thừa chỉ tổ sinh thêm một câu
     * UPDATE rỗng.</p>
     */
    private void closeOpenAttempt(OrderEntity order, PaymentStatus target) {
        if (order == null || order.getId() == null) {
            return;
        }

        Optional<PaymentEntity> lastAttempt =
                paymentRepository.findFirstByOrderIdOrderByAttemptNoDesc(order.getId());

        if (lastAttempt.isEmpty()) {
            // Đơn tạo trước CK-2 chưa có bản ghi payment nào — không có gì để đóng.
            return;
        }

        PaymentEntity payment = lastAttempt.get();

        // PAID: tiền đã về. Ghi đè thành CANCELLED/EXPIRED là làm mất sự thật quan trọng nhất
        // trong toàn bộ hệ thống. Phần hoàn tiền thuộc luồng khác.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        payment.setStatus(target);
    }

    /**
     * Đơn có được phép mở link VNPay hay không.
     *
     * <p>Mọi nhánh đều là lỗi <b>nghiệp vụ do client</b> gây ra nên dùng {@link ApiException} với
     * 409, không phải {@code IllegalArgumentException} (thứ dành cho hợp đồng nội bộ bị vi phạm,
     * sẽ ra 500).</p>
     */
    private void requirePayableByVnpay(OrderEntity order) {
        if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This order is paid on delivery (COD); VNPay is not available");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // Hết hạn cũng dẫn tới đây: quá hạn thì đơn đã bị huỷ và hàng đã trả kho.
            throw new ApiException(ErrorCode.CONFLICT,
                    "This order is cancelled and can no longer be paid");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This order is already " + order.getStatus() + " and can no longer be paid");
        }
        if (isPastDeadline(order)) {
            throw new ApiException(ErrorCode.CONFLICT, "Payment has expired");
        }
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PAID)) {
            throw new ApiException(ErrorCode.CONFLICT, "This order has already been paid");
        }
    }

    /**
     * Quyết định dùng lại lần thử cũ hay mở lần thử mới, rồi trả về bản ghi sẽ dựng link.
     *
     * <p><b>Quy tắc đã chốt:</b></p>
     * <ul>
     *   <li>{@code PENDING} ⇒ <b>dùng lại</b>: giữ nguyên {@code txnRef} và {@code expiresAt}. Đây
     *       là cơ chế "mở lại link không gia hạn thêm 15 phút".</li>
     *   <li>{@code FAILED} ⇒ mở lần thử mới, {@code txnRef} mới, nhưng <b>hạn vẫn là hạn cũ</b>
     *       (lấy từ đơn, không cộng thêm).</li>
     *   <li>{@code CANCELLED} ⇒ <b>từ chối</b>. Đây là ca <b>khách chủ động huỷ thanh toán</b>,
     *       khác hẳn thất bại: huỷ là khách đổi ý, không được tự động mở lần mới. Hai trạng thái do
     *       hai đường khác nhau ghi (IPN thất bại → {@code FAILED}; khách bấm huỷ → {@code CANCELLED}),
     *       nên chỉ cần chặn {@code CANCELLED} là đủ để không retry sau khi khách đổi ý.</li>
     *   <li>{@code EXPIRED} ⇒ <b>từ chối</b>. Đơn đã bị huỷ và hàng đã trả kho; khách phải đặt đơn mới.</li>
     * </ul>
     */
    private PaymentEntity resolveAttemptFor(OrderEntity order) {
        Optional<PaymentEntity> lastAttempt =
                paymentRepository.findFirstByOrderIdOrderByAttemptNoDesc(order.getId());

        if (lastAttempt.isEmpty()) {
            // Đơn tạo trước CK-2 nên chưa có bản ghi payment nào — vẫn phải phục vụ được.
            return createAttempt(order, 1);
        }

        PaymentEntity last = lastAttempt.get();

        return switch (last.getStatus()) {
            case PENDING -> last;
            case FAILED -> createAttempt(order, last.getAttemptNo() + 1);
            case CANCELLED -> throw new ApiException(ErrorCode.CONFLICT,
                    "This payment was cancelled; place a new order to pay again");
            case EXPIRED -> throw new ApiException(ErrorCode.CONFLICT,
                    "Payment has expired; place a new order to pay again");
            case PAID -> throw new ApiException(ErrorCode.CONFLICT, "This order has already been paid");
        };
    }

    /**
     * Mở một lần thử thanh toán mới.
     *
     * <p>{@code expiresAt} <b>luôn</b> lấy từ {@code order.paymentDueAt}, không bao giờ tính lại —
     * đó là điểm chặn việc gia hạn bằng cách bấm thanh toán lại nhiều lần.</p>
     */
    private PaymentEntity createAttempt(OrderEntity order, int attemptNo) {
        return paymentRepository.save(PaymentEntity.builder()
                .order(order)
                .txnRef(newTxnRef())
                .amount(order.getTotal())
                .status(PaymentStatus.PENDING)
                .expiresAt(order.getPaymentDueAt())
                .attemptNo(attemptNo)
                .build());
    }

    private boolean isPastDeadline(OrderEntity order) {
        return order.getPaymentDueAt() != null && !Instant.now().isBefore(order.getPaymentDueAt());
    }

    /**
     * {@code txnRef} = UUID <b>bỏ dấu gạch</b>, sinh độc lập cho mỗi lần thử.
     *
     * <p>Không nhét số đơn vào đây: payment đã liên kết với đơn qua {@code order_id}. Một UUID 32 ký
     * tự vừa khớp giới hạn {@code Alphanumeric[1,100]} của VNPay, vừa không thể đoán để dò giao dịch
     * của đơn khác. Ràng buộc unique trên cột là lớp chặn cuối nếu có trùng.</p>
     */
    private String newTxnRef() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Mã đơn khách đọc trên hoá đơn luôn ở dạng chữ hoa; chuẩn hoá để tra không trượt. */
    private String normalizeOrderNumber(String orderNumber) {
        return orderNumber == null ? null : orderNumber.trim().toUpperCase(Locale.ROOT);
    }
}
