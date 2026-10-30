package com.structure.springboot.features.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Nhịp và giới hạn của tác vụ quét đơn hết hạn, gom từ {@code app.order.expiry.*}.
 *
 * <p><b>Vì sao tách khỏi {@link OrderProperties}:</b> hai record phục vụ hai đối tượng khác nhau.
 * {@code OrderProperties} là <i>chính sách kinh doanh</i> — phí ship, ngưỡng freeship, hạn thanh
 * toán — thứ bộ phận vận hành chỉnh theo từng đợt. Record này là <i>tham số vận hành kỹ thuật</i>:
 * nhịp quét, kích thước lô, số lần thử lại. Gộp chung thì mỗi lần chỉnh nhịp quét là mỗi lần động
 * vào class mà mọi nghiệp vụ đơn hàng đang dùng, và ngược lại. Tách ra thì tác vụ nền là một khối
 * độc lập, nhìn tên class là biết nó thuộc về cái gì.</p>
 *
 * <p><b>Bốn giá trị, ba mức chặn sai cấu hình.</b> Một tác vụ chạy nền sai cấu hình không làm ứng
 * dụng chết ngay — nó lặng lẽ quét quá dày hoặc bỏ sót đơn — nên ở đây kiểm chặt hơn bình thường:
 * nhịp quét, thời gian chờ lượt đầu, và số lần thử lại đều phải dương; kích thước lô phải nằm trong
 * khoảng hợp lý. Sai thì {@code IllegalArgumentException} ngay lúc bind properties, tức là lúc khởi
 * động.</p>
 *
 * @param scanInterval     khoảng cách giữa hai lượt quét
 * @param scanInitialDelay thời gian chờ trước lượt quét đầu tiên
 * @param retryDelay       thời gian tối thiểu trước khi hỏi lại VNPay về một đơn chưa có kết luận
 * @param batchSize        số đơn tối đa xử lý trong một lượt
 * @param maxQueryAttempts số lần tối đa hỏi VNPay cho một đơn không có kết luận rõ ràng
 * @param maxAgeHours      chỉ xét đơn có hạn thanh toán trong vòng bao nhiêu giờ gần đây
 */
@ConfigurationProperties(prefix = "app.order.expiry")
public record OrderExpiryProperties(
        Duration scanInterval,
        Duration scanInitialDelay,
        Duration retryDelay,
        Integer batchSize,
        Integer maxQueryAttempts,
        Integer maxAgeHours
) {

    /**
     * Trần nhịp quét. Quét dày hơn mức này là nhiều lần gọi VNPay hơn mà không thêm đơn nào được xử
     * lý — đơn chỉ quá hạn theo phút.
     */
    private static final Duration MAX_SCAN_INTERVAL = Duration.ofMinutes(10);

    /**
     * Sàn nhịp quét. Dày hơn một phút thì tác vụ chồng lên chính nó khi một lượt chậm, và mỗi lượt
     * lại một loạt câu hỏi sang VNPay.
     */
    private static final Duration MIN_SCAN_INTERVAL = Duration.ofSeconds(30);

    /** Trần kích thước lô: mỗi đơn một câu hỏi sang VNPay, lô lớn là một lượt quét rất dài. */
    private static final int MAX_BATCH_SIZE = 500;

    /** Trần số lần thử: quá mức này thì phải có người xem, không phải máy hỏi thêm. */
    private static final int MAX_QUERY_ATTEMPTS_LIMIT = 20;

    public OrderExpiryProperties {
        scanInterval = scanInterval != null ? scanInterval : Duration.ofMinutes(1);
        scanInitialDelay = scanInitialDelay != null ? scanInitialDelay : Duration.ofSeconds(30);
        retryDelay = retryDelay != null ? retryDelay : Duration.ofMinutes(5);
        batchSize = batchSize != null ? batchSize : 100;
        maxQueryAttempts = maxQueryAttempts != null ? maxQueryAttempts : 5;
        maxAgeHours = maxAgeHours != null ? maxAgeHours : 24;

        if (scanInterval.compareTo(MIN_SCAN_INTERVAL) < 0
                || scanInterval.compareTo(MAX_SCAN_INTERVAL) > 0) {
            throw new IllegalArgumentException(
                    "app.order.expiry.scan-interval must be between " + MIN_SCAN_INTERVAL
                            + " and " + MAX_SCAN_INTERVAL);
        }
        if (scanInitialDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "app.order.expiry.scan-initial-delay must not be negative");
        }
        if (retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException(
                    "app.order.expiry.retry-delay must be positive");
        }
        if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "app.order.expiry.batch-size must be between 1 and " + MAX_BATCH_SIZE);
        }
        if (maxQueryAttempts <= 0 || maxQueryAttempts > MAX_QUERY_ATTEMPTS_LIMIT) {
            throw new IllegalArgumentException(
                    "app.order.expiry.max-query-attempts must be between 1 and "
                            + MAX_QUERY_ATTEMPTS_LIMIT);
        }
        if (maxAgeHours <= 0) {
            throw new IllegalArgumentException(
                    "app.order.expiry.max-age-hours must be positive");
        }
    }
}
