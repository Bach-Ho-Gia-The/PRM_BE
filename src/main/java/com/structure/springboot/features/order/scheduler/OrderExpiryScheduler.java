package com.structure.springboot.features.order.scheduler;

import com.structure.springboot.features.order.dto.response.OrderExpirySummary;
import com.structure.springboot.features.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Tác vụ nền quét đơn quá hạn thanh toán.
 *
 * <h2>Lớp này cố ý mỏng</h2>
 *
 * <p>Nó chỉ lấy giờ hiện tại, gọi service, rồi ghi một dòng log. Toàn bộ logic nằm ở
 * {@link OrderService#expireOverdueOrders}. Lý do rất thực dụng: một phương thức {@code @Scheduled}
 * gần như không test được — muốn test phải chờ đồng hồ thật hoặc mock hạ tầng scheduler của Spring.
 * Đặt logic vào service thì test gọi thẳng hàm đó với mốc thời gian tự chọn, còn lớp này chỉ còn năm
 * dòng không có gì để sai.</p>
 *
 * <h2>Ngoại lệ bị nuốt, có chủ ý</h2>
 *
 * <p>Lượt quét sau vẫn phải chạy dù lượt này hỏng. Ném ra khỏi phương thức {@code @Scheduled} thì
 * Spring chỉ ghi log rồi thôi — nhưng nếu sau này có ai đổi sang cấu hình scheduler khác, hành vi đó
 * không còn được bảo đảm. Bắt tại đây là một dòng, và làm rõ rằng đây là quyết định chứ không phải
 * sơ suất. {@code expireOverdueOrders} đã tự bảo đảm không ném cho từng đơn; đây là lưới cuối cho
 * những lỗi ngoài dự kiến như mất kết nối database.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryScheduler {

    private final OrderService orderService;

    /**
     * Chạy theo lịch, đọc nhịp từ {@code app.order.expiry.scan-interval} và
     * {@code app.order.expiry.scan-initial-delay}.
     *
     * <p>{@code initialDelayString} — chờ một khoảng trước lượt đầu tiên: quét ngay lúc khởi động là
     * quét trong lúc ứng dụng còn đang nạp bean và chưa sẵn sàng phục vụ, và trên môi trường có
     * nhiều instance khởi động cùng lúc thì tất cả cùng hỏi VNPay một loạt.</p>
     *
     * <p>{@link OrderExpiryProperties} đã chặn nhịp quá dày hoặc quá thưa lúc bind properties, nên ở
     * đây không kiểm lại.</p>
     */
    @Scheduled(
            fixedDelayString = "${app.order.expiry.scan-interval}",
            initialDelayString = "${app.order.expiry.scan-initial-delay}"
    )
    public void scan() {
        try {
            OrderExpirySummary summary = orderService.expireOverdueOrders(Instant.now());

            // Một dòng cho mỗi lượt, kể cả lượt không có đơn nào: im lặng hoàn toàn thì không phân
            // biệt được "không có đơn quá hạn" với "tác vụ đã chết".
            log.info(summary.toLogLine());

            if (!summary.isConsistent()) {
                // Không thể xảy ra theo cấu trúc code, nhưng nếu xảy ra thì nó nói rằng có đơn bị
                // đếm sai hoặc bị bỏ qua — và đó là thứ phải biết ngay, không phải suy luận ra.
                log.error("Order expiry summary is inconsistent: {}", summary.toLogLine());
            }

        } catch (RuntimeException exception) {
            log.error("Order expiry scan failed; next scan will retry", exception);
        }
    }
}
