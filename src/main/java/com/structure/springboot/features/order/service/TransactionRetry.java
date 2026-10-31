package com.structure.springboot.features.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Chạy một đơn vị công việc có giao dịch và <b>tự thử lại khi gặp tranh chấp khoá tạm thời</b>
 * (deadlock bị database huỷ, hoặc hết thời gian chờ khoá).
 *
 * <h2>Vì sao cần</h2>
 *
 * <p>Luồng đặt/huỷ đơn và quét hết hạn đều khoá bi quan nhiều dòng cùng lúc (dòng đơn + nhiều dòng
 * {@code product_variants}). Khoá theo <b>ID tăng dần</b> đã làm deadlock hiếm đi, nhưng
 * {@code ProductVariantRepository} tự ghi rõ: sắp thứ tự <b>giảm chứ không triệt tiêu</b> — vẫn phải
 * xử lý ở tầng gọi. Khi deadlock thật sự xảy ra, Postgres huỷ một giao dịch ("kẻ thua") và Spring
 * ném {@link PessimisticLockingFailureException}. Đây là lỗi <b>tạm thời</b>: chạy lại gần như chắc
 * chắn thành công vì giao dịch thắng đã xong và nhả khoá.</p>
 *
 * <h2>Bốn nguyên tắc, và chỗ này giữ đúng cả bốn</h2>
 *
 * <ol>
 *   <li><b>Retry nằm NGOÀI giao dịch.</b> Helper chỉ gọi {@code unitOfWork} — người gọi truyền vào
 *       một lời gọi method {@code @Transactional} <b>qua proxy</b> ({@code self.getObject()...}), nên
 *       mỗi lần thử mở một giao dịch mới. Đặt retry bên trong một method {@code @Transactional} sẽ
 *       vô dụng: giao dịch đã bị đánh dấu chết, thử lại trong cùng nó không bao giờ commit được.</li>
 *   <li><b>Đơn vị công việc phải chạy lại được.</b> Trách nhiệm của người gọi — các luồng ở đây đều
 *       đọc-khoá-kiểm lại trong giao dịch nên chạy lần hai là an toàn.</li>
 *   <li><b>Chỉ bắt lỗi tạm thời.</b> Bắt đúng {@link PessimisticLockingFailureException} — cha chung
 *       của {@code DeadlockLoserDataAccessException} và {@code CannotAcquireLockException}. <b>Không</b>
 *       bắt {@code OptimisticLockingFailureException} (ca xung đột phiên bản mà dự án cố ý trả về cho
 *       client "refresh rồi thử lại") hay lỗi nghiệp vụ — nuốt nhầm chúng là giấu lỗi thật.</li>
 *   <li><b>Giới hạn + backoff có jitter.</b> Tối đa {@value #MAX_ATTEMPTS} lần; chờ một khoảng
 *       <b>ngẫu nhiên nhỏ</b> giữa các lần để hai kẻ thua không cùng thử lại đồng pha rồi lại đâm vào
 *       nhau.</li>
 * </ol>
 *
 * <p>Là lớp tiện ích tĩnh, không phải bean: nó không giữ trạng thái và không phụ thuộc gì để tiêm.
 * Để tĩnh thì các service đang có không phải thêm một phụ thuộc vào hàm dựng.</p>
 */
@Slf4j
public final class TransactionRetry {

    private TransactionRetry() {
    }

    /** Số lần thử tối đa. Deadlock-loser gần như luôn qua ở lần thứ hai; ba là dư an toàn. */
    static final int MAX_ATTEMPTS = 3;

    /** Mốc backoff cơ sở; mỗi lần thử chờ khoảng này nhân số lần, cộng jitter ngẫu nhiên. */
    private static final long BASE_BACKOFF_MILLIS = 50L;

    /**
     * Chạy {@code unitOfWork}, thử lại khi gặp {@link PessimisticLockingFailureException}.
     *
     * @param description mô tả ngắn để ghi log khi phải thử lại hoặc bỏ cuộc
     * @param unitOfWork  lời gọi method {@code @Transactional} <b>qua proxy</b> — xem nguyên tắc 1
     * @return kết quả của lần chạy thành công
     * @throws PessimisticLockingFailureException nếu vẫn thất bại sau {@link #MAX_ATTEMPTS} lần
     */
    public static <T> T execute(String description, Supplier<T> unitOfWork) {
        int attempt = 1;
        while (true) {
            try {
                return unitOfWork.get();
            } catch (PessimisticLockingFailureException contention) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("{}: bo cuoc sau {} lan vi tranh chap khoa (deadlock/lock timeout)",
                            description, attempt);
                    throw contention;
                }
                log.debug("{}: tranh chap khoa o lan thu {}, thu lai", description, attempt);
                backoff(attempt);
                attempt++;
            }
        }
    }

    private static void backoff(int attempt) {
        long jitter = ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MILLIS);
        long millis = BASE_BACKOFF_MILLIS * attempt + jitter;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            // Khôi phục cờ ngắt rồi ném ra — nuốt InterruptedException là che mất yêu cầu dừng luồng.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off before retry", interrupted);
        }
    }
}
