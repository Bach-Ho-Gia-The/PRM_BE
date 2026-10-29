package com.structure.springboot.features.payment.service;

import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.payment.dto.response.PaymentResponse;
import com.structure.springboot.features.payment.dto.response.VnpayPaymentUrlResponse;
import com.structure.springboot.features.payment.entity.PaymentEntity;
import com.structure.springboot.features.payment.enums.RefundReason;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentService {

    /**
     * Ghi nhận <b>lần thử thanh toán đầu tiên</b> cho một đơn vừa được tạo. Gọi trong chính giao dịch
     * của việc tạo đơn, nên đơn và payment luôn ra đời cùng nhau hoặc cùng không ra đời.
     *
     * <p>Áp cho <b>cả COD</b>: đơn COD vẫn có bản ghi payment để shop bấm "đã thu tiền" sau này;
     * chỉ khác là {@code expiresAt = null} vì không có hạn thanh toán.</p>
     *
     * @param order đơn <b>đã được lưu</b> (cần có id và số tiền)
     * @return bản ghi payment vừa tạo
     */
    PaymentEntity createFirstAttempt(OrderEntity order);

    /**
     * Mở link thanh toán VNPay cho một đơn của chính khách đang đăng nhập.
     *
     * <p>Không phải lần gọi nào cũng sinh bản ghi mới — xem quy tắc ở phần cài đặt. Điểm quan trọng
     * nhất: <b>mở lại link không bao giờ gia hạn thêm thời gian</b>.</p>
     *
     * @param clientIp IP của khách, dùng cho tham số {@code vnp_IpAddr}
     */
    VnpayPaymentUrlResponse startVnpayPayment(UUID accountId, String orderNumber, String clientIp);

    /** Trạng thái thanh toán của một đơn của chính khách đang đăng nhập. Chỉ đọc. */
    PaymentResponse getMyPayment(UUID accountId, String orderNumber);

    /**
     * Đóng lần thử thanh toán đang mở của một đơn vừa bị huỷ, đánh dấu {@code CANCELLED}.
     *
     * <p><b>Đây là việc bắt buộc, không phải tuỳ chọn.</b> Huỷ đơn mà để payment kẹt {@code PENDING}
     * là trạng thái bất khả thi: bản ghi thanh toán sống mãi trong khi đơn đã chết, và mọi phép
     * đếm "đơn đang chờ tiền" đều sai.</p>
     *
     * <p>Kết quả theo trạng thái payment mới nhất:</p>
     * <ul>
     *   <li>{@code PENDING} ⇒ {@code CANCELLED}</li>
     *   <li>{@code PAID} ⇒ <b>không đụng</b>. Tiền đã về thì không được ghi đè lịch sử; phần hoàn
     *       tiền do luồng hoàn tiền lo, không phải hàm này.</li>
     *   <li>không có payment, hoặc đã {@code CANCELLED}/{@code EXPIRED}/{@code FAILED} ⇒ không làm gì
     *       (<b>idempotent</b> — gọi lại nhiều lần vô hại)</li>
     * </ul>
     *
     * <p>Phải gọi <b>trong cùng giao dịch</b> với việc đổi trạng thái đơn: đơn huỷ mà payment không
     * đóng được thì cả hai cùng rollback, không để lại nửa vời.</p>
     */
    void releaseForOrderCancel(OrderEntity order);

    /**
     * Đóng lần thử thanh toán đang mở vì đơn <b>quá hạn thanh toán</b>, đánh dấu {@code EXPIRED}.
     *
     * <p>Tách khỏi {@link #releaseForOrderCancel} chứ không gộp thành một hàm có tham số trạng thái:
     * "khách đổi ý" và "hết hạn" là hai nghiệp vụ khác nhau, gộp lại thì người gọi truyền sai lúc
     * nào không biết. Cùng lý do dự án không gộp hàm huỷ đơn của khách với hàm đổi trạng thái của shop.</p>
     *
     * <p>Quy tắc giống hệt {@link #releaseForOrderCancel}, chỉ khác trạng thái đích: {@code PAID}
     * không bị đụng, các trạng thái đã dứt điểm thì bỏ qua.</p>
     */
    void expireForOrder(OrderEntity order);

    /**
     * Thời điểm đơn đã được trả tiền, {@code null} nếu chưa.
     *
     * <p>Trả {@code Instant} chứ không trả cả DTO vì phía đơn hàng chỉ cần đúng một dữ kiện này để
     * hiển thị — giao diện bên ngoài không nên biết tới hình dạng bản ghi thanh toán.</p>
     *
     * <p>Phải đi qua service chứ không đọc thẳng bảng {@code payments}: tầng nghiệp vụ của một
     * feature không được chạm repository của feature khác.</p>
     */
    Instant findPaidAtForOrder(UUID orderId);

    /**
     * Ghi nhận một khoản <b>cần hoàn</b> cho đơn, <b>chỉ khi</b> đơn đã thực sự được trả tiền.
     *
     * <p>Hàm <b>idempotent theo đơn</b>: gọi nhiều lần cho cùng một đơn thì vẫn chỉ có đúng một
     * khoản hoàn. Lý do không phải cho đẹp — có tới bốn đường cùng có thể gọi hàm này cho một đơn:
     * khách huỷ đơn đã trả tiền, shop huỷ, IPN báo đã trả tiền sau khi đơn đã chết, và IPN gửi
     * lặp lại. Đường thứ hai và thứ tư <b>sẽ</b> xảy ra trong thực tế, không phải giả định.</p>
     *
     * <h2>Hàm này KHÔNG làm gì trong hai trường hợp</h2>
     * <ul>
     *   <li><b>Đơn chưa từng được trả tiền</b> — huỷ đơn chưa thanh toán thì không có gì để hoàn.
     *       Đây là ca thường gặp nhất, nên nó là nhánh <b>phải</b> rõ ràng chứ không phải ngoại lệ
     *       hiếm.</li>
     *   <li><b>Đơn đã có khoản hoàn rồi</b> — gọi lại thì im lặng bỏ qua, <b>không</b> ném
     *       {@code ApiException}: người gọi không làm gì sai, chỉ là sự kiện đến lần thứ hai.</li>
     * </ul>
     *
     * <p><b>Không hoàn tiền tự động.</b> Hàm chỉ ghi nhận nghĩa vụ, trạng thái là {@code PENDING}.
     * Việc chuyển tiền thật cho khách do shop làm qua cổng thanh toán và đánh dấu ở đợt sau —
     * "cần hoàn" và "đã hoàn" là hai chuyện khác nhau, đánh đồng là nói dối khách.</p>
     *
     * @param order  đơn vừa bị huỷ hoặc vừa nhận được tiền muộn
     * @param reason lý do, phân biệt "khách huỷ đơn đã trả tiền" với "IPN về muộn"
     */
    void requestRefundIfPaid(OrderEntity order, RefundReason reason);

    /**
     * Đọc đơn của một payment <b>kèm khoá ghi trên dòng đơn</b>, dùng riêng cho việc xác nhận
     * thanh toán từ cổng.
     *
     * <p><b>Vì sao phải khoá:</b> tác vụ quét hết hạn (CK-5) và IPN có thể chạy cùng lúc. Nếu IPN
     * không khoá đơn thì kịch bản sau xảy ra được: IPN đọc thấy đơn {@code PENDING}, tác vụ quét
     * huỷ đơn và hoàn kho, IPN ghi tiếp và đặt payment {@code PAID} — khách mất tiền, đơn bị huỷ,
     * hàng đã về kho, và khoản hoàn thì không ai tạo vì lúc IPN đọc thì đơn còn sống. Khoá dòng đơn
     * làm hai bên xếp hàng: ai vào sau đọc được trạng thái đã đổi.</p>
     *
     * <p><b>Vì sao hàm này nằm ở đây chứ không để tầng payment tự inject {@code OrderRepository}:</b>
     * quy ước của dự án là không đi tắt qua repository của feature khác. Ranh giới feature đi qua
     * service. {@code PaymentService} đã có sẵn {@code OrderRepository} từ CK-2 (dùng để khoá đơn
     * trong {@code startVnpayPayment}) nên đặt ở đây không sinh phụ thuộc mới, và cũng không tạo
     * vòng bean vì {@code OrderService} → {@code PaymentService} đã là chiều phụ thuộc có sẵn.</p>
     *
     * @param orderId  mã đơn lấy từ payment; {@code null} thì trả rỗng
     * @return đơn đã khoá, hoặc rỗng nếu không tồn tại
     */
    Optional<OrderEntity> findOrderForConfirmation(UUID orderId);
}
