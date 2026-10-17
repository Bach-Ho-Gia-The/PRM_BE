package com.structure.springboot.infrastructure.external.cloudinary;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ManagedImageServiceTest {

    private final CloudinaryService cloudinary = mock(CloudinaryService.class);
    private final ManagedImageService service = new ManagedImageService(cloudinary,
            new CloudinaryProperties("cloud", "key", "secret", "prm/dev", DataSize.ofMegabytes(5),
                    Set.of("jpg", "png", "webp"), Duration.ofSeconds(30)));

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
    void legacyJsonCannotSetManagedUrlButMayKeepSeedOrGoogleUrl() {
        service.requireUnmanagedUrl("/images/products/seed.jpg");
        service.requireUnmanagedUrl("https://lh3.googleusercontent.com/avatar.png");
        service.requireUnmanagedUrl("https://res.cloudinary.com/demo/image/upload/other-folder/seed.png");
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.requireUnmanagedUrl(
                        "https://res.cloudinary.com/cloud/image/upload/v123/prm/dev/products/image.png"))
                .getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.requireUnmanagedUrl("https://cdn.example/prm%2Fdev/profiles/one.webp"))
                .getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.requireUnmanagedUrl("not a valid URI"))
                .getErrorCode());
        verifyNoInteractions(cloudinary);
    }

    @Test
    void deletionFailureIsReturnedToCallerAndLoggedWithoutRetry() {
        String oldId = "prm/dev/profiles/old";
        UUID profileId = UUID.randomUUID();
        doThrow(new ApiException(ErrorCode.BAD_GATEWAY, "upstream details"))
                .when(cloudinary).delete(oldId);

        ApiException error = assertThrows(ApiException.class,
                () -> service.deleteOrFail(oldId, "profile", profileId));

        assertEquals(ErrorCode.BAD_GATEWAY, error.getErrorCode());
        verify(cloudinary).delete(oldId); // Exactly one attempt, not after-commit logging.
    }

    @Test
    void externalOrMissingPublicIdCannotTriggerCloudinaryDeletion() {
        service.deleteOrFail(null, "product", UUID.randomUUID());
        service.deleteOrFail("", "collection", UUID.randomUUID());
        verifyNoInteractions(cloudinary);
    }
}
