package com.structure.springboot.features.profile.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.profile.dto.request.ProfileUpdateRequest;
import com.structure.springboot.features.profile.entity.ProfileEntity;
import com.structure.springboot.features.profile.mapper.ProfileMapper;
import com.structure.springboot.features.profile.repository.ProfileRepository;
import com.structure.springboot.features.profile.service.impl.ProfileServiceImpl;
import com.structure.springboot.infrastructure.external.cloudinary.CloudinaryService;
import com.structure.springboot.infrastructure.external.cloudinary.ImageUploadResult;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    @Mock private ProfileRepository profiles;
    @Mock private AccountRepository accounts;
    @Mock private ManagedImageService images;
    @Mock private CloudinaryService cloudinary;
    private ProfileServiceImpl service;
    private final MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});

    @BeforeEach
    void setUp() {
        ProfileMapper mapper = Mappers.getMapper(ProfileMapper.class);
        service = new ProfileServiceImpl(profiles, accounts, mapper, images, cloudinary);
    }

    @Test
    void googleAvatarIsExternalAndUploadingMyAvatarDoesNotDeleteGoogleFile() {
        ProfileEntity profile = profile();
        profile.setAvatarUrl("https://lh3.googleusercontent.com/old.png");
        when(profiles.findWithLockByAccountId(profile.getAccount().getId())).thenReturn(Optional.of(profile));
        when(images.upload(file, "profiles"))
                .thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/profiles/new"));

        var result = service.uploadAvatar(profile.getAccount().getId(), file);

        assertEquals("https://cdn/new.png", result.avatarUrl());
        assertEquals("prm/dev/profiles/new", profile.getAvatarPublicId());
        verify(images).deleteOrFail(null, "profile", profile.getAccount().getId()); // No public ID to delete.
        assertFalse(java.util.List.of(result.getClass().getRecordComponents()).stream()
                .anyMatch(component -> component.getName().toLowerCase().contains("publicid")));
    }

    @Test
    void failureToDeleteOldAvatarAbortsUploadAndReturnsBadGateway() {
        ProfileEntity profile = profile();
        profile.setAvatarUrl("https://cdn/old.png");
        profile.setAvatarPublicId("prm/dev/profiles/old");
        UUID owner = profile.getAccount().getId();
        when(profiles.findWithLockByAccountId(owner)).thenReturn(Optional.of(profile));
        when(images.upload(file, "profiles"))
                .thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/profiles/new"));
        doThrow(new ApiException(ErrorCode.BAD_GATEWAY, "Cloudinary deletion failed"))
                .when(images).deleteOrFail("prm/dev/profiles/old", "profile", owner);

        ApiException error = assertThrows(ApiException.class, () -> service.uploadAvatar(owner, file));
        assertEquals(ErrorCode.BAD_GATEWAY, error.getErrorCode());
        verify(profiles).saveAndFlush(profile); // Not committed: Spring rolls back and cleans the new upload.
        verify(images).deleteOrFail("prm/dev/profiles/old", "profile", owner);
    }

    @Test
    void legacyPatchMayChooseExternalUrlButRemovesOldManagedAvatarOnce() {
        ProfileEntity profile = profile();
        profile.setAvatarUrl("https://cdn/old.png");
        profile.setAvatarPublicId("prm/dev/profiles/old");
        UUID owner = profile.getAccount().getId();
        when(profiles.findWithLockByAccountId(owner)).thenReturn(Optional.of(profile));
        var request = new ProfileUpdateRequest(null, null, null, null,
                "https://external.example/avatar.png", null);

        var result = service.updateMyProfile(owner, request);

        assertEquals(request.avatarUrl(), result.avatarUrl());
        assertNull(profile.getAvatarPublicId());
        verify(images).deleteOrFail("prm/dev/profiles/old", "profile", owner);
    }

    @Test
    void profilePatchRejectsManagedUrlWithoutChangingDatabase() {
        ProfileEntity profile = profile();
        UUID owner = profile.getAccount().getId();
        when(profiles.findWithLockByAccountId(owner)).thenReturn(Optional.of(profile));
        String forged = "https://res.cloudinary.com/cloud/image/upload/prm/dev/profiles/forged.jpg";
        doThrow(new ApiException(ErrorCode.BAD_REQUEST, "Managed URLs require upload"))
                .when(images).requireUnmanagedUrl(forged);

        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class, () -> service.updateMyProfile(
                owner, new ProfileUpdateRequest(null, null, null, null, forged, null)))
                .getErrorCode());
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void legacyAccountWithoutProfileCanUploadAfterAccountLockAndCreation() {
        AccountEntity account = AccountEntity.builder().id(UUID.randomUUID()).email("test@example.com").build();
        when(profiles.findWithLockByAccountId(account.getId())).thenReturn(Optional.empty());
        when(accounts.findWithLockById(account.getId())).thenReturn(Optional.of(account));
        when(profiles.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(images.upload(file, "profiles"))
                .thenReturn(new ImageUploadResult("https://cdn/new.png", "prm/dev/profiles/new"));

        var result = service.uploadAvatar(account.getId(), file);

        assertEquals("https://cdn/new.png", result.avatarUrl());
        verify(accounts).findWithLockById(account.getId());
    }

    @Test
    void accountDeletionCleansManagedAvatarAfterCommitWithoutBlockingAccountDeletion() {
        ProfileEntity profile = profile();
        profile.setAvatarPublicId("prm/dev/profiles/old");
        UUID owner = profile.getAccount().getId();
        when(profiles.findWithLockByAccountId(owner)).thenReturn(Optional.of(profile));

        service.deleteByAccountId(owner);

        verify(profiles).delete(profile);
        verify(cloudinary).deleteAfterCommit("prm/dev/profiles/old");
    }

    private static ProfileEntity profile() {
        AccountEntity account = AccountEntity.builder().id(UUID.randomUUID()).email("test@example.com").build();
        return ProfileEntity.builder().id(UUID.randomUUID()).account(account).build();
    }
}
