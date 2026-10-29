package com.structure.springboot.features.payment.service.impl;

import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.payment.dto.response.VnpayIpnResponse;
import com.structure.springboot.features.payment.entity.OrderRefundEntity;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.repository.OrderRefundRepository;
import com.structure.springboot.features.payment.repository.PaymentRepository;
import com.structure.springboot.features.payment.service.PaymentService;
import com.structure.springboot.features.payment.service.VnpayCallbackService;
import com.structure.springboot.infrastructure.external.vnpay.VnpayProperties;
import com.structure.springboot.infrastructure.external.vnpay.VnpaySignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Xử lý IPN của VNPay — nguồn xác nhận thanh toán duy nhất.
 *
 * <p><b>Vì sao khoá đơn đi qua {@code PaymentService} chứ không đọc thẳng {@code OrderRepository}:</b>
 * quy ước của dự án là không đi tắt qua repository của feature khác — ranh giới feature đi qua
 * service. Đi thẳng vào {@code OrderRepository} ở đây là chỗ vi phạm thứ ba trong cùng một luồng.
 * Đi qua {@code OrderService} thì lại tạo <b>vòng phụ thuộc bean</b> thật, vì {@code OrderService}
 * đã gọi {@code PaymentService} từ CK-2. Nên hàm {@code findOrderForConfirmation} được đặt trong
 * {@code PaymentService} — nơi {@code OrderRepository} đã có sẵn từ CK-2, không sinh phụ thuộc mới.
 * Xem javadoc của chính hàm đó.</p>
 *
 * <h2>Thứ tự xử lý là bắt buộc, không được đảo</h2>
 *
 * <ol>
 *   <li>Kiểm chữ ký — <b>trước mọi thứ khác</b>. Chưa chắc request do VNPay gửi thì không được
 *       chạm database.</li>
 *   <li>Tra payment theo {@code vnp_TxnRef}.</li>
 *   <li>So số tiền.</li>
 *   <li><b>Khoá đơn</b> rồi mới đổi trạng thái.</li>
 * </ol>
 *
 * <p>Bước 4 là ràng buộc từ CK-2b: nếu IPN không khoá đơn thì tác vụ quét hết hạn có thể huỷ đơn
 * <b>ngay sau</b> khi IPN vừa xác nhận đã trả tiền ⇒ mất tiền khách, mất đơn, hàng về kho.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayCallbackServiceImpl implements VnpayCallbackService {

    /** VNPay gửi {@code vnp_PayDate} theo giờ Việt Nam, không kèm múi giờ. */
    private static final DateTimeFormatter PAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Mã thành công của VNPay. */
    private static final String VNP_SUCCESS = "00";

    private final PaymentRepository paymentRepository;
    private final OrderRefundRepository orderRefundRepository;
    private final PaymentService paymentService;
    private final VnpayProperties vnpayProperties;

    @Override
    @Transactional
    public VnpayIpnResponse handleIpn(Map<String, String> parameters) {
        // ── Bước 1: chữ ký. Sai thì dừng ngay, KHÔNG đọc database. ──
        if (!VnpaySignature.verify(parameters, vnpayProperties.hashSecret())) {
            // Không log toàn bộ tham số: chúng do người lạ gửi, có thể chứa dữ liệu độc hại.
            log.warn("VNPay IPN rejected: invalid signature for txnRef={}",
                    parameters.get("vnp_TxnRef"));
            return VnpayIpnResponse.of(VnpayIpnResponse.RSP_INVALID_SIGNATURE,
                    "Invalid signature", null);
        }

        String txnRef = parameters.get("vnp_TxnRef");

        // ── Bước 2: tra payment ──
        PaymentEntity payment = paymentRepository.findByTxnRef(txnRef).orElse(null);
        if (payment == null) {
            log.warn("VNPay IPN for unknown txnRef={}", txnRef);
            return VnpayIpnResponse.of(VnpayIpnResponse.RSP_ORDER_NOT_FOUND,
                    "Order not found", null);
        }

        // ── Bước 3: so số tiền. vnp_Amount đã nhân 100 nên phải chia lại. ──
        if (!amountMatches(parameters.get("vnp_Amount"), payment.getAmount())) {
            log.warn("VNPay IPN amount mismatch for txnRef={}: gateway={}, stored={}",
                    txnRef, parameters.get("vnp_Amount"), payment.getAmount());
            return VnpayIpnResponse.of(VnpayIpnResponse.RSP_INVALID_AMOUNT,
                    "Invalid amount", payment.getStatus());
        }

        // ── Bước 4: KHOÁ ĐƠN. Từ đây mới được đổi trạng thái. ──
        OrderEntity order = paymentService.findOrderForConfirmation(payment.getOrder().getId())
                .orElse(null);
        if (order == null) {
            // Có payment mà không có đơn: dữ liệu hỏng. Trả 01 để VNPay gọi lại, và log đủ để
            // người vận hành truy ra.
            log.error("VNPay IPN: payment {} points to a missing order", payment.getId());
            return VnpayIpnResponse.of(VnpayIpnResponse.RSP_ORDER_NOT_FOUND,
                    "Order not found", payment.getStatus());
        }

        PaymentStatus previous = payment.getStatus();
        String responseCode = parameters.get("vnp_ResponseCode");

        if (!VNP_SUCCESS.equals(responseCode)) {
            return handleFailure(payment, order, parameters, previous);
        }
        return handleSuccess(payment, order, parameters, previous);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Nhánh thành công (vnp_ResponseCode = 00)
    // ══════════════════════════════════════════════════════════════════════════

    private VnpayIpnResponse handleSuccess(PaymentEntity payment, OrderEntity order,
                                          Map<String, String> parameters,
                                          PaymentStatus previous) {
        // Đã PAID rồi: IPN gửi lặp. KHÔNG đổi gì, trả 02 để VNPay ngừng gọi lại.
        if (previous == PaymentStatus.PAID) {
            log.info("VNPay IPN duplicate for txnRef={} — already PAID", payment.getTxnRef());
            return VnpayIpnResponse.alreadyConfirmed(previous);
        }

        applyGatewayFields(payment, parameters);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(resolvePaidAt(parameters));

        // Đơn đã chết (khách huỷ / hết hạn) mà tiền vẫn về ⇒ L16.
        // Đơn GIỮ CANCELLED, KHÔNG hoàn kho lần hai, nhưng PHẢI ghi khoản hoàn.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("VNPay IPN: payment confirmed for CANCELLED order {} — recording refund",
                    order.getOrderNumber());
            OrderRefundEntity refund = recordRefund(order, payment, RefundReason.PAID_AFTER_CANCEL);
            return VnpayIpnResponse.paidAfterCancel(previous, refund);
        }

        // Ca bình thường: đơn vẫn PENDING (luật 4 — thanh toán xong KHÔNG tự xác nhận đơn).
        log.info("VNPay IPN: payment {} PAID for order {}", payment.getTxnRef(), order.getOrderNumber());
        return VnpayIpnResponse.success(previous);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Nhánh thất bại (vnp_ResponseCode ≠ 00)
    // ══════════════════════════════════════════════════════════════════════════

    private VnpayIpnResponse handleFailure(PaymentEntity payment, OrderEntity order,
                                          Map<String, String> parameters,
                                          PaymentStatus previous) {
        // Tiền đã về rồi thì IPN thất bại đến sau (do retry, do lệch mã) KHÔNG được lùi trạng
        // thái: lùi là xoá dấu vết một giao dịch đã thu tiền thật.
        if (previous == PaymentStatus.PAID) {
            log.warn("VNPay IPN failure code={} for already PAID txnRef={} — kept PAID",
                    parameters.get("vnp_ResponseCode"), payment.getTxnRef());
            return VnpayIpnResponse.alreadyConfirmed(previous);
        }

        // Đã dứt điểm rồi: idempotent.
        if (previous != PaymentStatus.PENDING) {
            return VnpayIpnResponse.alreadyConfirmed(previous);
        }

        applyGatewayFields(payment, parameters);
        payment.setStatus(PaymentStatus.FAILED);

        // QUAN TRỌNG: đơn KHÔNG bị huỷ ở đây. Khách đóng app giữa lúc nhập OTP vẫn còn thời gian
        // thử lại; chỉ tác vụ quét hết hạn mới được huỷ đơn. Huỷ ở đây là tước mất quyền thử lại
        // của khách vì một lần bấm nhầm.
        log.info("VNPay IPN: payment {} FAILED (code={}) for order {} — order untouched",
                payment.getTxnRef(), parameters.get("vnp_ResponseCode"), order.getOrderNumber());
        return VnpayIpnResponse.success(previous);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Hàm dùng chung
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Ghi các trường VNPay trả về lên payment.
     *
     * <p>Ghi cả khi thất bại: mã lỗi là dữ kiện để shop và khách đối chiếu sau này, và để trang
     * Return hiển thị được lý do cụ thể thay vì "không rõ".</p>
     */
    private void applyGatewayFields(PaymentEntity payment, Map<String, String> parameters) {
        payment.setGatewayTransactionNo(parameters.get("vnp_TransactionNo"));
        payment.setResponseCode(parameters.get("vnp_ResponseCode"));
        payment.setGatewayTransactionStatus(parameters.get("vnp_TransactionStatus"));
        payment.setBankCode(parameters.get("vnp_BankCode"));
    }

    /**
     * Giờ giao dịch lấy từ {@code vnp_PayDate} — giờ thật ở cổng thanh toán.
     *
     * <p><b>Không dùng {@code Instant.now()}</b>: IPN có thể về muộn vài phút (VNPay retry tối đa
     * 10 lần, cách nhau 5 phút), nên giờ nhận được không phải giờ khách trả tiền. Chênh vài phút
     * nghe nhỏ, nhưng nó là con số duy nhất dùng để đối soát với sao kê ngân hàng.</p>
     *
     * <p>Nếu {@code vnp_PayDate} thiếu hoặc sai định dạng thì <b>vẫn ghi nhận thanh toán</b> và
     * lùi về {@code now()} kèm log WARN. Từ chối IPN chỉ vì một trường phụ là đánh đổi một thông
     * tin lấy toàn bộ giao dịch — không đáng.</p>
     */
    private Instant resolvePaidAt(Map<String, String> parameters) {
        String payDate = parameters.get("vnp_PayDate");
        if (payDate == null || payDate.isBlank()) {
            log.warn("VNPay IPN: vnp_PayDate missing for txnRef={} — falling back to now()",
                    parameters.get("vnp_TxnRef"));
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(payDate, PAY_DATE_FORMAT)
                    .atZone(vnpayProperties.zoneId())
                    .toInstant();
        } catch (DateTimeParseException exception) {
            log.warn("VNPay IPN: unparseable vnp_PayDate={} for txnRef={} — falling back to now()",
                    payDate, parameters.get("vnp_TxnRef"));
            return Instant.now();
        }
    }

    /**
     * So số tiền VNPay gửi với số tiền đã lưu.
     *
     * <p>{@code vnp_Amount} là số tiền <b>đã nhân 100</b> theo quy định của VNPay. So trực tiếp hai
     * con số là sai gấp trăm lần, và lỗi này không tự lộ ra vì nó chỉ làm mọi IPN bị từ chối với
     * mã {@code 04}.</p>
     *
     * <p>Dùng {@link BigDecimal#compareTo} chứ không {@code equals}: {@code equals} so cả
     * {@code scale}, nên {@code 1030000} và {@code 1030000.00} bị coi là khác nhau dù cùng giá trị.
     * Số tiền trong database là {@code NUMERIC(12,2)} nên luôn có scale 2, còn phép chia ở đây cho
     * scale 0 — so bằng {@code equals} là mọi IPN đều trượt.</p>
     */
    private boolean amountMatches(String gatewayAmount, BigDecimal storedAmount) {
        if (gatewayAmount == null || storedAmount == null) {
            return false;
        }
        try {
            BigDecimal fromGateway = new BigDecimal(gatewayAmount).movePointLeft(2);
            return fromGateway.compareTo(storedAmount) == 0;
        } catch (NumberFormatException exception) {
            // Dữ liệu rác từ nơi khác gửi tới. Trả false ⇒ RspCode 04, VNPay gọi lại.
            return false;
        }
    }

    /**
     * Ghi khoản hoàn nếu chưa có — idempotent, không ném khi trùng.
     *
     * <p>Không gọi {@code PaymentService.requestRefundIfPaid}: hàm đó đọc lại payment từ database
     * để tự xác định "đã trả tiền chưa", trong khi ở đây ta <b>đang cầm</b> payment trong tay và
     * vừa set {@code PAID}. Đọc lại chỉ tốn thêm một câu SQL và có nguy cơ đọc phải bản cũ nếu
     * chưa flush.</p>
     */
    private OrderRefundEntity recordRefund(OrderEntity order, PaymentEntity payment,
                                           RefundReason reason) {
        return orderRefundRepository.findByOrderId(order.getId())
                .orElseGet(() -> orderRefundRepository.save(OrderRefundEntity.builder()
                        .order(order)
                        .payment(payment)
                        // payments.amount, KHÔNG phải orders.total — xem javadoc OrderRefundEntity.
                        .amount(payment.getAmount())
                        .reason(reason)
                        .build()));
    }
}
