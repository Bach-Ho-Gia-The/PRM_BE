package com.structure.springboot.features.order.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.order.dto.request.OrderCancelRequest;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.dto.request.OrderStatusUpdateRequest;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.payment.enums.VnpayQueryVerdict;
import com.structure.springboot.features.order.dto.response.OrderExpirySummary;
import com.structure.springboot.features.order.dto.response.OrderResponse;
import com.structure.springboot.features.order.dto.response.OrderSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface OrderService {

    /**
     * Đặt hàng. Backend tự tra giá từ DB, tự tính tiền và tự trừ tồn kho — mọi con số tiền bạc
     * client gửi lên đều bị bỏ qua.
     */
    OrderResponse createOrder(UUID accountId, OrderRequest request);

    /** Danh sách đơn của chính mình, mới nhất trước. */
    PageResponse<OrderSummaryResponse> getMyOrders(UUID accountId, Pageable pageable);

    /**
     * Chi tiết một đơn của chính mình.
     *
     * <p>Mã đơn của người khác trả <b>404</b> chứ không phải 403: 403 là gián tiếp xác nhận
     * "mã đơn này có thật", đủ để người ta dò ra mã đơn hợp lệ.</p>
     */
    OrderResponse getMyOrderByNumber(UUID accountId, String orderNumber);

    /**
     * Tài khoản này còn đơn hàng nào không.
     *
     * <p>Mở ra cho module tài khoản hỏi trước khi xoá. Không để {@code AccountServiceImpl} tiêm
     * thẳng {@code OrderRepository}: dữ liệu của module nào thì module đó giữ, bên ngoài đi qua
     * service — đúng hướng phụ thuộc mà {@code profileService} đang dùng ở cùng chỗ đó.</p>
     */
    boolean hasOrders(UUID accountId);

    /** Màn quản trị: mọi đơn của mọi người, lọc theo trạng thái nếu có. */
    PageResponse<OrderSummaryResponse> getOrders(OrderStatus status, Pageable pageable);

    /** Màn quản trị: chi tiết một đơn bất kỳ, tra theo id chứ không theo mã đơn. */
    OrderResponse getOrderById(UUID id);

    /**
     * Màn quản trị: tra một đơn bất kỳ theo mã khách đọc được.
     *
     * <p>Khách gọi điện thì đọc {@code LBVN-2610-A7K2} chứ không đọc UUID. Thiếu đường này thì
     * admin phải lật từng trang danh sách để tìm.</p>
     */
    OrderResponse getOrderByNumber(String orderNumber);

    /**
     * Admin chuyển trạng thái đơn. Huỷ đơn sẽ hoàn kho.
     */
    OrderResponse updateStatus(UUID id, OrderStatusUpdateRequest request);

    /**
     * Khách tự huỷ đơn của chính mình.
     *
     * <p>Chỉ huỷ được khi đơn còn {@code PENDING}: từ {@code CONFIRMED} trở đi shop đã gói hàng,
     * muốn huỷ thì nhắn shop. Tra theo {@code orderNumber} + tài khoản nên không ai huỷ được đơn
     * của người khác; không tìm thấy thì trả 404 chứ không 403 để khỏi lộ mã đơn có tồn tại.</p>
     */
    OrderResponse cancelMyOrder(UUID accountId, String orderNumber, OrderCancelRequest request);

    /**
     * Quét các đơn đã quá hạn thanh toán: hỏi VNPay kết quả thật rồi mới quyết định huỷ.
     *
     * <h2>Vì sao phải hỏi VNPay trước khi huỷ</h2>
     *
     * <p>{@code status = PENDING} sau khi quá hạn <b>không</b> có nghĩa là khách chưa trả tiền. Nó
     * chỉ có nghĩa là <b>IPN chưa về</b>. Huỷ thẳng là huỷ đơn của một người đã trả tiền, và hoàn
     * hàng về kho trong khi khách đang chờ nhận.</p>
     *
     * <h2>Ba chặng, và vì sao không gộp</h2>
     *
     * <ol>
     *   <li><b>Đọc</b> danh sách ứng viên — một câu truy vấn, không khoá.</li>
     *   <li><b>Hỏi VNPay</b> cho từng đơn, <b>ngoài mọi giao dịch</b>. Giữ khoá dòng đơn trong lúc
     *       chờ HTTP là tự bắn vào chân: IPN của chính đơn đó cần cùng dòng khoá, nên VNPay chậm
     *       mười giây là IPN bị chặn mười giây — trong khi IPN mới là nguồn xác nhận thanh toán.
     *       </li>
     *   <li><b>Chốt</b> — mỗi đơn một giao dịch ngắn, nạp lại <b>có khoá</b> rồi mới quyết. Giữa
     *       chặng 2 và chặng 3, IPN có thể vừa về; không kiểm lại thì chặng 2 nói "thất bại", IPN
     *       vừa ghi "đã trả tiền", và ta huỷ một đơn vừa thu tiền.</li>
     * </ol>
     *
     * <h2>Bốn kết cục, và chỉ một trong bốn được huỷ</h2>
     *
     * <ul>
     *   <li>VNPay nói <b>đã trả tiền</b> ⇒ ghi payment {@code PAID}, <b>không</b> đụng đơn. Đơn vẫn
     *       {@code PENDING} chờ shop xác nhận — thanh toán xong không có nghĩa là đơn được duyệt.
     *       </li>
     *   <li>VNPay nói <b>thất bại</b> hoặc <b>không có giao dịch</b> ⇒ huỷ đơn, hoàn kho, payment
     *       {@code EXPIRED}.</li>
     *   <li>VNPay nói <b>không rõ</b> ⇒ <b>không đụng gì</b>, hẹn kiểm tra lại.</li>
     *   <li>Đơn đã có payment {@code PAID} (kể cả khi chặng 2 lỗi) ⇒ không huỷ. Đây là lớp chặn
     *       cuối: tin database của mình hơn tin một câu trả lời không rõ ràng.</li>
     * </ul>
     *
     * <h2>Không ném ngoại lệ ra ngoài — và cách điều đó được bảo đảm</h2>
     *
     * <p>Đây là hàm chạy trong tác vụ nền: một ngoại lệ lọt ra làm hỏng cả lượt quét và bỏ sót mọi
     * đơn còn lại. Có <b>hai</b> lớp giữ bất biến này, không phải một:</p>
     *
     * <ol>
     *   <li>Bản thân các lời gọi trong vòng lặp không ném vì lý do nghiệp vụ —
     *       {@code PaymentService.queryVnpayForOrder} cam kết trả {@code UNKNOWN} thay vì ném, và
     *       {@link #settleExpiredOrder} không ném với dữ liệu bất thường.</li>
     *   <li>Nhưng lỗi <b>hạ tầng</b> vẫn có thể xảy ra (mất kết nối database giữa vòng lặp, lỗi
     *       khoá). Vì vậy <b>chính tác vụ nền</b> ({@code OrderExpiryScheduler.scan}) bọc toàn bộ
     *       lời gọi trong {@code try/catch} và ghi log. Một lượt hỏng thì lượt sau chạy lại.</li>
     * </ol>
     *
     * <p><b>Điều hàm này KHÔNG làm:</b> nó không bắt lỗi cho từng đơn riêng lẻ. Nếu một đơn làm ném
     * ngoại lệ, lượt quét dừng ở đó và các đơn còn lại <b>không</b> được xử lý trong lượt này — chỉ
     * lượt sau mới thử lại. Đây là đánh đổi có ý thức: bắt lỗi từng đơn sẽ che mất lỗi hạ tầng lặp
     * lại, và một lỗi hạ tầng im lặng còn nguy hiểm hơn một lượt quét bị bỏ.</p>
     *
     * @param now mốc thời gian của lượt quét, do người gọi truyền vào. Truyền vào chứ không gọi
     *            {@code Instant.now()} bên trong để test điều khiển được thời gian mà không phải
     *            mock đồng hồ hệ thống
     * @return bản tóm tắt bốn con số, để tác vụ nền ghi một dòng log duy nhất
     */
    OrderExpirySummary expireOverdueOrders(Instant now);

    /**
     * Chặng chốt của việc quét hết hạn: nạp lại đơn <b>có khoá</b>, kiểm lại lần cuối, rồi huỷ nếu
     * còn đủ điều kiện.
     *
     * <p><b>Vì sao đây là phương thức công khai của interface chứ không phải hàm private:</b>
     * {@code @Transactional} của Spring chỉ có tác dụng khi lời gọi đi qua proxy. Gọi một phương
     * thức private (hoặc gọi public trong cùng đối tượng) thì không có giao dịch nào được mở, và
     * khoá dòng đơn sẽ nhả ngay sau câu {@code SELECT ... FOR UPDATE} — tức là mất sạch tác dụng.
     * Vì vậy nó buộc phải nằm trên interface để tác vụ quét gọi được qua proxy.</p>
     *
     * <p>Dù công khai, hàm này <b>không phải API nghiệp vụ</b>: không có controller nào gọi nó, và
     * nó từ chối mọi đơn không còn {@code PENDING}. Gọi nhầm nó với một đơn đang sống chỉ trả về
     * {@code false}.</p>
     *
     * @param orderId mã đơn
     * @param verdict kết luận của VNPay — chỉ dùng để ghi log, quyết định đã chốt ở chặng trước
     * @param now     mốc thời gian của lượt quét
     * @return {@code true} nếu lần này thực sự huỷ đơn
     */
    boolean settleExpiredOrder(UUID orderId, VnpayQueryVerdict verdict, Instant now);
}
