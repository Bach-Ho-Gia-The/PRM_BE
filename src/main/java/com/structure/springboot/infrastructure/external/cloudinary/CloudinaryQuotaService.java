package com.structure.springboot.infrastructure.external.cloudinary;

import com.cloudinary.Cloudinary;
import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryQuotaService {

    private final Cloudinary cloudinary;
    private final CloudinaryQuotaProperties properties;

    // Chỉ đọc/ghi bên trong getState(), được bảo vệ bằng synchronized.
    private QuotaState cachedState;

    public void checkUploadAllowed() {
        if (!properties.enabled()) {
            return;
        }

        QuotaState state = getState();

        // Không lấy được usage: chặn upload thay vì mặc định cho phép.
        if (state.usedPercent() == null) {
            throw new ApiException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Image uploads are temporarily unavailable because usage could not be verified"
            );
        }

        if (state.usedPercent() >= properties.blockAtPercent()) {
            throw new ApiException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Image uploads are temporarily disabled because the usage limit has been reached"
            );
        }
    }

    /**
     * Chỉ một thread được gọi API để cập nhật cache tại một thời điểm
     * trong cùng instance ứng dụng.
     */
    private synchronized QuotaState getState() {
        long now = System.nanoTime();

        if (cachedState != null
                && now - cachedState.expiresAtNanos() < 0) {
            return cachedState;
        }

        try {
            Map<?, ?> response = cloudinary.api().usage(Map.of());

            double usedPercent = extractUsedPercent(response);

            cachedState = createState(
                    usedPercent,
                    properties.cacheTtl()
            );

            return cachedState;
        } catch (Exception exception) {
            /*
             * Cache cả trạng thái lỗi trong retry-delay để tránh gọi lại
             * Cloudinary cho từng request khi upstream đang gặp sự cố.
             *
             * Không log nguyên message/stack trace SDK tại đây,
             * tránh vô tình đưa thông tin xác thực vào log.
             */
            log.warn(
                    "Cloudinary usage check failed; uploads blocked for {}. Error type: {}",
                    properties.retryDelay(),
                    exception.getClass().getSimpleName()
            );

            cachedState = createState(
                    null,
                    properties.retryDelay()
            );

            return cachedState;
        }
    }

    private double extractUsedPercent(Map<?, ?> response) {
        if (response == null
                || !(response.get("credits") instanceof Map<?, ?> credits)) {
            throw new IllegalStateException(
                    "Cloudinary usage response is missing credits"
            );
        }

        /*
         * Ưu tiên tự tính từ usage / limit nếu có đủ dữ liệu,
         * tránh phụ thuộc vào tỷ lệ phần trăm đã được làm tròn.
         */
        Double usage = nonNegativeNumber(credits.get("usage"));
        Double limit = nonNegativeNumber(credits.get("limit"));

        if (usage != null && limit != null && limit > 0) {
            double percent = (usage / limit) * 100.0;

            if (Double.isFinite(percent)) {
                return percent;
            }
        }

        // Fallback khi response chỉ có tỷ lệ sử dụng.
        Double usedPercent = nonNegativeNumber(
                credits.get("used_percent")
        );

        if (usedPercent != null) {
            return usedPercent;
        }

        /*
         * Không coi thiếu dữ liệu là usage = 0:
         * không xác minh được quota thì phải chặn upload.
         */
        throw new IllegalStateException(
                "Cloudinary usage response has no valid credit percentage"
        );
    }

    private Double nonNegativeNumber(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }

        double result = number.doubleValue();

        if (!Double.isFinite(result) || result < 0) {
            return null;
        }

        return result;
    }

    private QuotaState createState(
            Double usedPercent,
            Duration lifetime
    ) {
        return new QuotaState(
                usedPercent,
                System.nanoTime() + lifetime.toNanos()
        );
    }

    /**
     * usedPercent = null nghĩa là lần kiểm tra usage thất bại.
     * expiresAtNanos chỉ dùng nội bộ để kiểm tra thời hạn cache.
     */
    private record QuotaState(
            Double usedPercent,
            long expiresAtNanos
    ) {
    }
}