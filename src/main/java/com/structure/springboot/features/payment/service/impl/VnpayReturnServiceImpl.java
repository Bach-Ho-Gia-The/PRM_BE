package com.structure.springboot.features.payment.service.impl;

import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.payment.dto.response.VnpayReturnResponse;
import com.structure.springboot.features.payment.enums.VnpayReturnState;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.PaymentStatus;
import com.structure.springboot.features.payment.repository.PaymentRepository;
import com.structure.springboot.features.payment.service.VnpayReturnService;
import com.structure.springboot.infrastructure.external.vnpay.VnpayProperties;
import com.structure.springboot.infrastructure.external.vnpay.VnpaySignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Dựng trang kết quả thanh toán — <b>chỉ đọc</b>.
 *
 * <p>Không có {@code save} nào trong class này, và đó là chủ ý: xem javadoc ở
 * {@link VnpayReturnService}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayReturnServiceImpl implements VnpayReturnService {

    private static final String VNP_SUCCESS = "00";

    private static final DateTimeFormatter PAID_AT_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final PaymentRepository paymentRepository;
    private final VnpayProperties vnpayProperties;

    @Override
    @Transactional(readOnly = true)
    public VnpayReturnResponse buildReturnView(Map<String, String> parameters) {
        // ── 1. Chữ ký. Vai trò DUY NHẤT: chứng minh URL do VNPay sinh ra. ──
        // Không chứng minh đã thanh toán — chỉ IPN làm được. Nên sai chữ ký thì đây là link giả,
        // và ta KHÔNG đọc database để hiển thị bất cứ điều gì về đơn của người khác.
        if (!VnpaySignature.verify(parameters, vnpayProperties.hashSecret())) {
            log.warn("VNPay Return: invalid signature for txnRef={}", parameters.get("vnp_TxnRef"));
            return VnpayReturnResponse.invalidLink();
        }

        String txnRef = parameters.get("vnp_TxnRef");

        // ── 2. Trạng thái LUÔN đọc từ database ──
        PaymentEntity payment = paymentRepository.findByTxnRef(txnRef).orElse(null);
        if (payment == null) {
            log.warn("VNPay Return: unknown txnRef={}", txnRef);
            return VnpayReturnResponse.txnRefNotFound(txnRef);
        }

        String orderNumber = payment.getOrder() == null ? null : payment.getOrder().getOrderNumber();
        String amountText = formatMoney(payment.getAmount());
        String code = parameters.get("vnp_ResponseCode");

        return switch (payment.getStatus()) {
            // Đơn đã huỷ mà tiền vẫn về (L16): khách PHẢI được nói rằng sẽ được hoàn tiền, chứ
            // không phải "thanh toán thành công" — câu đó khiến khách chờ hàng không bao giờ tới.
            case PAID -> payment.getOrder() != null
                    && payment.getOrder().getStatus() == OrderStatus.CANCELLED
                    ? VnpayReturnResponse.paidAfterCancel(
                            orderNumber, amountText, formatPaidAt(payment.getPaidAt()), txnRef, code)
                    : VnpayReturnResponse.success(
                            orderNumber, amountText, formatPaidAt(payment.getPaidAt()), txnRef, code);

            // PENDING: hai tình huống rất khác nhau, và khách cần được nói đúng cái nào.
            //  - Cổng báo thành công nhưng IPN chưa về: "đừng thanh toán lại, đợi vài phút".
            //  - Cổng báo thất bại: nói thẳng là thất bại.
            // Payment PENDING mà cổng báo 00 nghĩa là IPN chưa tới (hoặc đã hỏng) — KHÔNG phải
            // "chưa trả tiền". Hiển thị "thất bại" ở đây là mời khách trả tiền hai lần.
            case PENDING -> VNP_SUCCESS.equals(code)
                    ? VnpayReturnResponse.pendingConfirmation(orderNumber, amountText, txnRef, code)
                    : VnpayReturnResponse.failed(orderNumber, amountText, txnRef, code);

            // FAILED / EXPIRED / CANCELLED: chắc chắn không có tiền trong hệ thống.
            case FAILED, EXPIRED, CANCELLED ->
                    VnpayReturnResponse.failed(orderNumber, amountText, txnRef, code);
        };
    }

    /**
     * Đổi {@code vnp_Amount} của cổng sang số tiền thật.
     *
     * <p>Dùng {@link PaymentEntity#getAmount()} — số tiền <b>của mình</b>, đã lưu lúc tạo giao dịch
     * — chứ không dùng {@code vnp_Amount} trên URL. Hiển thị số tiền do URL khai là để người lạ
     * quyết định con số khách nhìn thấy.</p>
     */
    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        // DecimalFormat KHÔNG thread-safe, nên tạo bản mới mỗi lần gọi. Trang này không phải
        // đường nóng (mỗi khách một lần), đổi lại là không có khả năng hai request cùng lúc ra
        // cùng một chuỗi số tiền sai.
        DecimalFormat format = new DecimalFormat(
                "#,##0", DecimalFormatSymbols.getInstance(Locale.of("vi", "VN")));
        return format.format(amount) + " ₫";
    }

    private String formatPaidAt(Instant paidAt) {
        if (paidAt == null) {
            return null;
        }
        ZoneId zoneId = vnpayProperties.zoneId();
        return PAID_AT_FORMAT.format(paidAt.atZone(zoneId));
    }
}
