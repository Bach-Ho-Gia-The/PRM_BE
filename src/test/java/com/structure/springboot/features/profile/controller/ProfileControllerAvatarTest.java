package com.structure.springboot.features.profile.controller;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.security.AuthenticatedUser;
import com.structure.springboot.features.profile.dto.response.ProfileResponse;
import com.structure.springboot.features.profile.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileControllerAvatarTest {

    private final ProfileService service = mock(ProfileService.class);
    private final ProfileController controller = new ProfileController(service);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1});

    @Test
    void avatarUploadUsesIdentityFromTokenNotClientInput() throws Exception {
        UUID owner = UUID.randomUUID();
        var principal = new AuthenticatedUser(owner, "owner@example.com");
        var profile = mock(ProfileResponse.class);
        when(service.uploadAvatar(owner, file)).thenReturn(profile);
        when(request.getRequestURI()).thenReturn("/profiles/me/avatar");

        var response = controller.uploadMyAvatar(principal, file, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(profile, response.getBody().getResult());
        verify(service).uploadAvatar(owner, file);
        assertEquals("/me/avatar", ProfileController.class.getMethod("uploadMyAvatar",
                AuthenticatedUser.class, org.springframework.web.multipart.MultipartFile.class,
                HttpServletRequest.class).getAnnotation(PutMapping.class).value()[0]);
    }

    @Test
    void anonymousUploadIsRejectedRatherThanUpdatingAnArbitraryProfile() {
        ApiException error = assertThrows(ApiException.class,
                () -> controller.uploadMyAvatar(null, file, request));
        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
    }
}
