package com.structure.springboot.features.order.service;

import com.structure.springboot.features.order.config.OrderExpiryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bộ đếm trong bộ nhớ: đơn nào đã hỏi VNPay rồi mà chưa có kết luận, hỏi lại lúc nào, hỏi mấy lần.
 *
 * <h2>Vì sao cần, bằng con số</h2>
 *
 * <p>Không có bộ đếm này thì mỗi lượt quét (mỗi phút) sẽ hỏi lại <b>toàn bộ</b> đơn chưa có kết luận.
 * Một trăm đơn kẹt ở trạng thái không rõ là một trăm câu hỏi mỗi phút, kéo dài vô hạn — trong khi
 * câu trả lời gần như chắc chắn vẫn là "không rõ". Đó là đốt hạn mức gọi API của VNPay để nhận lại
 * đúng thứ mình đã biết.</p>
 *
 * <h2>Hai mức chặn</h2>
 *
 * <ol>
 *   <li><b>Chờ tối thiểu</b> ({@code retryDelay}) trước khi hỏi lại cùng một đơn.</li>
 *   <li><b>Số lần tối đa</b> ({@code maxQueryAttempts}). Chạm trần thì <b>thôi hỏi</b> và ghi log
 *       WARN để người vận hành xem tay. Một đơn mà VNPay không trả lời dứt khoát sau năm lần hỏi
 *       không phải chuyện của máy nữa — tiếp tục hỏi chỉ làm chậm những đơn khác.</li>
 * </ol>
 *
 * <h2>Vì sao trong bộ nhớ, và cái giá phải trả</h2>
 *
 * <p>Dự án chốt <b>không thêm cột</b> vào bảng {@code orders} cho việc này, và chốt chạy <b>một
 * instance</b>. Trong hai điều kiện đó, bộ nhớ là lựa chọn đúng: không đổi schema, không thêm truy
 * vấn ghi vào một bảng đang là đường nóng của toàn hệ.</p>
 *
 * <p><b>Cái giá, phải nói rõ:</b> khởi động lại ứng dụng là mất sạch bộ đếm. Sau khi restart, mọi
 * đơn chưa có kết luận sẽ được hỏi lại một lần và đếm lại từ đầu. Đó là lý do truy vấn quét còn có
 * <b>cận dưới thời gian</b> ({@code maxAgeHours}): đơn quá cũ không được nạp lại nữa, nên một lần
 * restart không làm bùng nổ số câu hỏi cho những đơn đã bị bỏ quên từ lâu.</p>
 *
 * <p><b>Vì sao {@link ConcurrentHashMap} chứ không phải map thường:</b> tác vụ quét có thể chạy
 * chồng lên chính nó nếu một lượt chậm hơn một phút (mỗi câu hỏi VNPay có thể mất tới mười giây).
 * Hai luồng cùng ghi vào một {@code HashMap} là lỗi dữ liệu, không phải chuyện hiệu năng.</p>
 *
 * <p><b>Vì sao {@code compute} chứ không {@code get} rồi {@code put}:</b> {@code compute} chạy nguyên
 * tử trên một khoá; đọc rồi ghi thì hai luồng có thể cùng thấy "chưa tới hạn" rồi cùng hỏi VNPay.
 * Đây đúng là loại lỗi chỉ xuất hiện khi có tải, tức là lúc khó tái hiện nhất.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryCooldown {

    private final OrderExpiryProperties properties;

    private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * Đơn này có được phép hỏi VNPay ngay bây giờ không, và nếu được thì <b>đã ghi nhận</b> lần hỏi.
     *
     * <p>Ghi nhận ngay tại đây, <b>trước</b> khi câu hỏi được gửi đi, cố ý: nếu ghi sau khi có kết
     * quả thì một câu hỏi ném lỗi giữa đường sẽ không được đếm, và đơn đó quay lại lượt sau với số
     * lần thử y nguyên — trần số lần thử không bao giờ chạm, đúng thứ nó sinh ra để chặn.</p>
     *
     * @param orderId  mã đơn
     * @param now      mốc thời gian của lượt quét
     * @return {@code true} nếu được hỏi (và đã đếm), {@code false} nếu phải bỏ qua lượt này
     */
    public boolean tryAcquire(UUID orderId, Instant now) {
        // Cờ nằm trong closure: compute chạy nguyên tử trên một khoá nên chỉ đúng một luồng thấy
        // granted = true cho mỗi lần cấp. Đọc lại map sau compute (cách cũ) không phân biệt được
        // "vừa được cấp" với "đã có sẵn bản ghi từ trước".
        // ConcurrentHashMap ném NullPointerException với key null, và computeIfAbsent cũng vậy.
        // Không chặn trước thì một đơn thiếu id (dữ liệu hỏng) sẽ làm cả lượt quét chết thay vì
        // chỉ bỏ qua đúng đơn đó.
        if (orderId == null) {
            return false;
        }

        AtomicBoolean granted = new AtomicBoolean(false);

        attempts.compute(orderId, (key, current) -> {
            if (current == null) {
                granted.set(true);
                return new Attempt(1, now);
            }

            if (!current.isReadyForRetry(properties.retryDelay(), now)) {
                return current;
            }

            if (current.failures() >= properties.maxQueryAttempts()) {
                // Chạm trần: giữ nguyên bản ghi để lần sau vẫn thấy trần, không đếm lại từ đầu.
                return current;
            }

            granted.set(true);
            return new Attempt(current.failures() + 1, now);
        });

        return granted.get();
    }

    /**
     * Đơn đã có kết luận rõ ràng (đã trả tiền, hoặc đã bị huỷ) ⇒ quên nó đi.
     *
     * <p>Không quên thì bộ nhớ lớn dần theo số đơn đã xử lý trong suốt thời gian ứng dụng chạy — một
     * rò rỉ chậm, vài chục byte mỗi đơn, nhưng không có điểm dừng.</p>
     */
    public void forget(UUID orderId) {
        if (orderId != null) {
            attempts.remove(orderId);
        }
    }

    /** Số lần đã hỏi VNPay về đơn này. Chỉ dùng cho log và test. */
    public int failures(UUID orderId) {
        if (orderId == null) {
            return 0;
        }
        Attempt attempt = attempts.get(orderId);
        return attempt == null ? 0 : attempt.failures();
    }

    /** Đơn đã chạm trần số lần hỏi hay chưa. */
    public boolean isExhausted(UUID orderId) {
        return failures(orderId) >= properties.maxQueryAttempts();
    }

    /**
     * Một lần hỏi đã ghi nhận.
     *
     * @param failures      số lần đã hỏi tính cả lần này
     * @param lastAttemptAt thời điểm hỏi gần nhất
     */
    private record Attempt(int failures, Instant lastAttemptAt) {

        boolean isReadyForRetry(Duration retryDelay, Instant now) {
            return !lastAttemptAt.plus(retryDelay).isAfter(now);
        }
    }
}
