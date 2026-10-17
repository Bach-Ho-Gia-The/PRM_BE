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
        } catch (RuntimeException exception) {
            log.warn("Previous image deletion failed; entityType={}, entityId={}, publicId={}, errorType={}",
                    entityType, entityId, publicId, exception.getClass().getSimpleName());
            throw new ApiException(ErrorCode.BAD_GATEWAY,
                    "Image change cancelled: deletion of the previous image could not be confirmed. Please refresh");
        }
    }

    /**
     * Legacy JSON URL fields are for external/seed images only. A URL under the managed folder
     * must come from a server-side upload, or its public ID/ownership could be lost or forged.
     */
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
