package com.structure.springboot.infrastructure.external.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryReplacementLifecycleTest {

    private final Cloudinary sdk = mock(Cloudinary.class);
    private final Uploader uploader = mock(Uploader.class);
    private final CloudinaryQuotaService quota = mock(CloudinaryQuotaService.class);
    private final CloudinaryProperties properties = new CloudinaryProperties(
            "cloud", "key", "secret", "prm/dev", DataSize.ofMegabytes(5),
            Set.of("png", "jpg", "webp"), Duration.ofSeconds(30));
    private final CloudinaryService cloudinary = new CloudinaryService(sdk, properties, quota);
    private final ManagedImageService images = new ManagedImageService(cloudinary, properties);
    private final MockMultipartFile png = new MockMultipartFile("file", "avatar.png", "image/png",
            new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0});

    @BeforeEach
    void startTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void finishTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void failedOldDeletionAbortsRequestAndRollbackCleansNewFileExactlyOnce() throws Exception {
        when(sdk.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            return Map.of("secure_url", "https://res.cloudinary.com/cloud/image/upload/" + options.get("public_id") + ".png",
                    "public_id", options.get("public_id"));
        });
        String oldId = "prm/dev/profiles/old";
        doAnswer(invocation -> {
            if (oldId.equals(invocation.getArgument(0))) {
                throw new IOException("timeout after possible deletion");
            }
            return Map.of("result", "ok");
        }).when(uploader).destroy(any(String.class), anyMap());

        ImageUploadResult created = images.upload(png, "profiles");
        assertNotNull(created.publicId());
        ApiException error = assertThrows(ApiException.class,
                () -> images.deleteOrFail(oldId, "profile", UUID.randomUUID()));
        assertEquals(ErrorCode.BAD_GATEWAY, error.getErrorCode());
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(uploader).destroy(eq(oldId), anyMap());
        verify(uploader).destroy(eq(created.publicId()), anyMap());
    }

    @Test
    void completedTransactionDoesNotCleanNewlyUploadedFile() throws Exception {
        when(sdk.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            return Map.of("secure_url", "https://res.cloudinary.com/cloud/image/upload/" + options.get("public_id") + ".png",
                    "public_id", options.get("public_id"));
        });

        ImageUploadResult created = images.upload(png, "collections");
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(uploader, never()).destroy(eq(created.publicId()), anyMap());
    }
}
