package com.structure.springboot.infrastructure.external.cloudinary;

import com.cloudinary.Cloudinary;
import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CloudinaryService {


    private static final String SAFE_PATH =
            "[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*";

    private static final byte[] PNG_SIGNATURE = {
            (byte) 137, 80, 78, 71, 13, 10, 26, 10
    };

    private static final Set<String> WEBP_CHUNKS =
            Set.of("VP8 ", "VP8L", "VP8X");

    private static final Logger log =
            LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;
    private final CloudinaryQuotaService quotaService;

    /**
     * folder do backend chọn, ví dụ: profiles, collections, products.
     * Không lấy folder trực tiếp từ request của client.
     */
    public ImageUploadResult upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "Image file is required"
            );
        }

        if (folder == null || !folder.matches(SAFE_PATH)) {
            throw new IllegalArgumentException(
                    "Upload folder must be a safe, non-empty path"
            );
        }

        byte[] bytes = readFile(file);
        validateFormat(bytes, file.getContentType());
        requireConfigured(); // ktra biến môi trường

        // Kiểm tra trước khi gọi Upload API.
        // Đặt ngoài try/catch upload để giữ nguyên lỗi HTTP 503.
        quotaService.checkUploadAllowed();

        String publicId = properties.folderPrefix()
                + "/" + folder
                + "/" + UUID.randomUUID();

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    bytes,
                    Map.of(
                            "resource_type", "image",
                            "public_id", publicId,
                            "overwrite", false,
                            "allowed_formats",
                            properties.allowedFormats().toArray(String[]::new)
                    )
            );

            if (result.get("secure_url") instanceof String url
                    && url.startsWith("https://")
                    && publicId.equals(result.get("public_id"))) {
                return new ImageUploadResult(url, publicId);
            }

            throw new ApiException(
                    ErrorCode.BAD_GATEWAY,
                    "Image storage returned an invalid response"
            );
        } catch (IOException exception) {
            throw new ApiException(
                    ErrorCode.BAD_GATEWAY,
                    "Image upload failed"
            );
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }

            // Không trả nguyên lỗi SDK vì có thể chứa thông tin nội bộ.
            throw new ApiException(
                    ErrorCode.BAD_GATEWAY,
                    "Image upload failed"
            );
        }
    }

    /**
     * Feature phải kiểm tra quyền và việc ảnh còn được sử dụng trước khi gọi.
     * Kiểm tra prefix ở đây chỉ giới hạn phạm vi ảnh của ứng dụng.
     */
    public void delete(String publicId) {
        if (!StringUtils.hasText(publicId)
                || !publicId.startsWith(properties.folderPrefix() + "/")
                || !publicId.matches(SAFE_PATH)) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "Invalid managed image public ID"
            );
        }

        requireConfigured();

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                    publicId,
                    Map.of(
                            "resource_type", "image",
                            "invalidate", true
                    )
            );

            // Ảnh đã bị xóa được coi là thành công để có thể thử lại an toàn.
            if (!"ok".equals(result.get("result"))
                    && !"not found".equals(result.get("result"))) {
                throw new ApiException(
                        ErrorCode.BAD_GATEWAY,
                        "Image deletion failed"
                );
            }
        } catch (IOException exception) {
            throw new ApiException(
                    ErrorCode.BAD_GATEWAY,
                    "Image deletion failed"
            );
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }

            throw new ApiException(
                    ErrorCode.BAD_GATEWAY,
                    "Image deletion failed"
            );
        }
    }

    private byte[] readFile(MultipartFile file) {
        long maxBytes = properties.maxFileSize().toBytes();

        if (file.getSize() > maxBytes) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "Image exceeds the configured upload size limit"
            );
        }

        // Giới hạn cả số byte đọc thực tế, không chỉ tin metadata của file.
        try (InputStream stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes((int) (maxBytes + 1));

            if (bytes.length == 0) {
                throw new ApiException(
                        ErrorCode.BAD_REQUEST,
                        "Image file is required"
                );
            }

            if (bytes.length > maxBytes) {
                throw new ApiException(
                        ErrorCode.BAD_REQUEST,
                        "Image exceeds the configured upload size limit"
                );
            }

            return bytes;
        } catch (IOException exception) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "Cannot read image file"
            );
        }
    }

    private void validateFormat(byte[] bytes, String contentType) {
        String format = detectFormat(bytes);

        if (format == null || !properties.allowedFormats().contains(format)) {
            throw new ApiException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported image format"
            );
        }

        String actualMime = contentType == null
                ? ""
                : contentType.split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);

        String expectedMime = "jpg".equals(format)
                ? "image/jpeg"
                : "image/" + format;

        if (!expectedMime.equals(actualMime)) {
            throw new ApiException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Image content does not match its MIME type"
            );
        }
    }

    private String detectFormat(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 255) == 255
                && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) {
            return "jpg";
        }

        if (bytes.length >= PNG_SIGNATURE.length
                && Arrays.equals(
                Arrays.copyOf(bytes, PNG_SIGNATURE.length),
                PNG_SIGNATURE
        )) {
            return "png";
        }

        if (bytes.length >= 16
                && "RIFF".equals(ascii(bytes, 0))
                && "WEBP".equals(ascii(bytes, 8))
                && WEBP_CHUNKS.contains(ascii(bytes, 12))) {
            return "webp";
        }

        return null;
    }


    /**
     * Upload trong transaction của feature.
     * Nếu DB rollback, cố gắng xóa ảnh vừa upload.
     */
    public ImageUploadResult uploadInTransaction(
            MultipartFile file,
            String folder
    ) {
        requireTransaction();

        // Dùng lại upload() hiện có, bao gồm validation và quota guard.
        ImageUploadResult uploaded = upload(file, folder);

        try {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) {
                                deleteQuietly(uploaded.publicId());
                            }
                        }
                    }
            );
        } catch (RuntimeException exception) {
            // Nếu không đăng ký được callback thì dọn ảnh mới ngay.
            deleteQuietly(uploaded.publicId());
            throw exception;
        }

        return uploaded;
    }

    /**
     * Chỉ xóa ảnh sau khi DB commit thành công.
     * Caller phải bảo đảm ảnh không còn được đối tượng khác sử dụng.
     */
    public void deleteAfterCommit(String publicId) {
        // Ảnh seed/ảnh ngoài không có public ID: không gọi Cloudinary.
        if (!StringUtils.hasText(publicId)) {
            return;
        }

        requireTransaction();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteQuietly(publicId);
                    }
                }
        );
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "An active Spring transaction is required for image lifecycle operations"
            );
        }
    }

    /**
     * Lỗi dọn ảnh không được che mất lỗi DB hoặc biến một lần lưu
     * đã commit thành response thất bại.
     */
    private void deleteQuietly(String publicId) {
        try {
            delete(publicId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Image cleanup failed for publicId={}; errorType={}",
                    publicId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ApiException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Image storage is not configured"
            );
        }
    }
}