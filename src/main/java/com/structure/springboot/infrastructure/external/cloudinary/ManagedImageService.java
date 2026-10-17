package com.structure.springboot.infrastructure.external.cloudinary;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;

/** Shared image lifecycle for Product, Collection and Profile; no deletion retries or jobs. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagedImageService {

    private final CloudinaryService cloudinaryService;
    private final CloudinaryProperties properties;

    /** Register the newly uploaded file for a single cleanup attempt if the DB transaction rolls back. */
    public ImageUploadResult upload(MultipartFile file, String folder) {
        return cloudinaryService.uploadInTransaction(file, folder);
    }

    /**
     * Call only after staging/flushing the DB change, but before committing it. If deletion fails,
     * the exception rolls the transaction back; upload() then cleans up the new file once.
     * This is best effort, not a distributed transaction with Cloudinary.
     */
    public void deleteOrFail(String publicId, String entityType, UUID entityId) {
        if (!StringUtils.hasText(publicId)) {
            return; // Seed/Google/external images are never owned by this backend.
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("An active Spring transaction is required for managed image deletion");
        }
        try {
            cloudinaryService.delete(publicId);
        } catch (ApiException exception) {
            // publicId bẩn/không thuộc thư mục quản lý là lỗi DỮ LIỆU của chính backend.
            // Ép nó thành 502 "không xác nhận được việc xoá" sẽ che mất bug thật.
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Previous image deletion failed; entityType={}, entityId={}, publicId={}, errorType={}",
                    entityType, entityId, publicId, exception.getClass().getSimpleName());
            throw new ApiException(ErrorCode.BAD_GATEWAY,
                    "Image change cancelled: deletion of the previous image could not be confirmed. Please refresh");
        }
    }

    /**
     * Dọn ảnh SAU khi DB đã commit, dùng cho thao tác không phải thay ảnh (vd xoá tài khoản):
     * một lần xoá đã commit không được hỏng chỉ vì Cloudinary đang chập chờn.
     * Để ở đây để feature chỉ phải phụ thuộc một cửa duy nhất.
     */
    public void deleteAfterCommit(String publicId) {
        cloudinaryService.deleteAfterCommit(publicId);
    }

    /**
     * Legacy JSON URL fields are for external/seed images only. A URL under the managed folder
     * must come from a server-side upload, or its public ID/ownership could be lost or forged.
     */
    /**
     * URL ảnh do client tự nhập phải là http/https có host thật, nếu không ảnh hỏng sẽ nằm im
     * trong DB tới lúc người dùng mở trang mới lộ. Bao luôn {@link #requireUnmanagedUrl(String)}
     * nên tầng gọi chỉ cần một lời gọi.
     *
     * <p>Chuỗi rỗng được bỏ qua: với Product nó mang nghĩa "gỡ thumbnail", còn Collection tự
     * chặn rỗng trước khi gọi vào đây.</p>
     */
    public void requireExternalImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (uri.getHost() == null || !("http".equals(scheme) || "https".equals(scheme))) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Image URL must use http or https");
            }
        } catch (URISyntaxException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid image URL");
        }
        requireUnmanagedUrl(url);
    }

    public void requireUnmanagedUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        try {
            URI uri = new URI(url.trim());
            String path = uri.getPath(); // Decodes percent-escaped slashes as well.
            if (path != null && path.contains("/" + properties.folderPrefix() + "/")) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Managed image URLs must be set through the image upload or gallery API");
            }
        } catch (URISyntaxException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid image URL");
        }
    }
}
