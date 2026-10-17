package com.structure.springboot.features.collection.controller;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.collection.dto.request.CollectionProductsRequest;
import com.structure.springboot.features.collection.dto.request.CollectionUpdateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.dto.response.CollectionDetailResponse;
import com.structure.springboot.features.collection.dto.response.CollectionPublicDetailResponse;
import com.structure.springboot.features.collection.dto.response.CollectionPublicProductResponse;
import com.structure.springboot.features.collection.dto.response.CollectionPublicSummaryResponse;
import com.structure.springboot.features.collection.service.CollectionService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionControllerTest {

    private final CollectionService service = mock(CollectionService.class);
    private final CollectionController controller = new CollectionController(service);
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);

    @Test
    void adminMethodsAreUnderAdminPathAndPublicGetsHaveNoAdminGuard() {
        assertEquals("/collections", CollectionController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertNull(CollectionController.class.getAnnotation(PreAuthorize.class));

        int adminEndpoints = 0;
        int publicEndpoints = 0;
        for (Method method : CollectionController.class.getDeclaredMethods()) {
            String mapping = null;
            if (method.isAnnotationPresent(GetMapping.class)) {
                mapping = String.join("", method.getAnnotation(GetMapping.class).value());
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                mapping = String.join("", method.getAnnotation(PostMapping.class).value());
            } else if (method.isAnnotationPresent(PutMapping.class)) {
                mapping = String.join("", method.getAnnotation(PutMapping.class).value());
            } else if (method.isAnnotationPresent(PatchMapping.class)) {
                mapping = String.join("", method.getAnnotation(PatchMapping.class).value());
            } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                mapping = String.join("", method.getAnnotation(DeleteMapping.class).value());
            }
            if (mapping == null) {
                continue;
            }
            if (mapping.startsWith("/admin")) {
                adminEndpoints++;
                assertEquals("hasRole('ADMIN')", method.getAnnotation(PreAuthorize.class).value());
            } else {
                publicEndpoints++;
                assertNull(method.getAnnotation(PreAuthorize.class));
                if (mapping.isEmpty()) {
                    assertEquals("getPublicCollections", method.getName());
                } else {
                    assertEquals("/slug/{slug}", mapping);
                    assertEquals("getPublicCollectionBySlug", method.getName());
                }
            }
        }
        assertEquals(9, adminEndpoints);
        assertEquals(2, publicEndpoints);
    }

    @Test
    void coverUploadReceivesJsonVersionPartAndMultipartFile() throws NoSuchMethodException {
        Method action = CollectionController.class.getMethod("uploadCover", UUID.class,
                CollectionVersionRequest.class, MultipartFile.class, HttpServletRequest.class);
        assertEquals("/admin/{id}/cover", action.getAnnotation(PutMapping.class).value()[0]);
        assertEquals(MediaType.MULTIPART_FORM_DATA_VALUE, action.getAnnotation(PutMapping.class).consumes()[0]);
        assertEquals("metadata", action.getParameters()[1].getAnnotation(RequestPart.class).value());
        assertEquals("file", action.getParameters()[2].getAnnotation(RequestPart.class).value());
        assertEquals("hasRole('ADMIN')", action.getAnnotation(PreAuthorize.class).value());

        UUID id = UUID.randomUUID();
        var body = new CollectionVersionRequest(3L);
        var file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});
        var updated = new CollectionDetailResponse(id, "Summer", "summer", "https://cdn/new.png",
                RecordStatus.HIDDEN, 4L, List.of(), 0, null, null);
        when(service.uploadCover(id, body, file)).thenReturn(updated);
        when(httpRequest.getRequestURI()).thenReturn("/collections/admin/" + id + "/cover");
        var response = controller.uploadCover(id, body, file, httpRequest);
        assertEquals(updated, response.getBody().getResult());
        verify(service).uploadCover(id, body, file);
    }

    @Test
    void publicListAlwaysUsesPublicResponseWithoutStatusOrVersion() {
        var pageable = PageRequest.of(0, 20);
        var card = new CollectionPublicSummaryResponse(UUID.randomUUID(), "Summer", "summer", null);
        var page = new PageResponse<>(List.of(card), 0, 20, 1L, 1, true, true);
        when(service.getPublicCollections(pageable)).thenReturn(page);
        when(httpRequest.getRequestURI()).thenReturn("/collections");

        var response = controller.getPublicCollections(pageable, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(page, response.getBody().getResult());
        assertTrue(List.of(CollectionPublicSummaryResponse.class.getRecordComponents()).stream()
                .noneMatch(field -> field.getName().equals("status") || field.getName().equals("version")));
        verify(service).getPublicCollections(pageable);
    }

    @Test
    void publicDetailUsesPagedProductCardsWithoutAdminFields() {
        var pageable = PageRequest.of(1, 1, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));
        var product = new CollectionPublicProductResponse(UUID.randomUUID(), "Shirt", "shirt", null,
                new BigDecimal("100.00"), null, "New");
        var detail = new CollectionPublicDetailResponse(UUID.randomUUID(), "Summer", "summer", null,
                new PageResponse<>(List.of(product), 1, 1, 2L, 2, false, true));
        when(service.getPublicCollectionBySlug("summer", pageable)).thenReturn(detail);
        when(httpRequest.getRequestURI()).thenReturn("/collections/slug/summer");

        var response = controller.getPublicCollectionBySlug("summer", pageable, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(detail, response.getBody().getResult());
        assertTrue(List.of(CollectionPublicDetailResponse.class.getRecordComponents()).stream()
                .noneMatch(field -> field.getName().equals("status") || field.getName().equals("version")));
        verify(service).getPublicCollectionBySlug("summer", pageable);
    }

    @Test
    void putUsesNumericVersionInDtoAndReturnsNewVersionInJson() {
        UUID id = UUID.randomUUID();
        CollectionProductsRequest body = new CollectionProductsRequest(4L, List.of(UUID.randomUUID()));
        CollectionDetailResponse updated = new CollectionDetailResponse(
                id, "Summer", "summer", null, RecordStatus.HIDDEN, 5L, List.of(), 0L, null, null);
        when(service.replaceProducts(id, body)).thenReturn(updated);
        when(httpRequest.getRequestURI()).thenReturn("/collections/admin/" + id + "/products");

        var response = controller.replaceProducts(id, body, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getHeaders().getETag());
        assertEquals(5L, response.getBody().getResult().version());
        verify(service).replaceProducts(id, body);
    }

    @Test
    void deleteUsesNumericQueryParamWithoutRequestBody() {
        UUID id = UUID.randomUUID();
        when(httpRequest.getRequestURI()).thenReturn("/collections/admin/" + id);

        var response = controller.deleteCollection(id, 4L, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).deleteCollection(id, 4L);
    }

    @Test
    void dtoVersionIsRequiredAndCannotBeNegative() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new CollectionProductsRequest(null, List.of(UUID.randomUUID()))).isEmpty());
            assertFalse(validator.validate(new CollectionProductsRequest(-1L, List.of(UUID.randomUUID()))).isEmpty());
            assertFalse(validator.validate(new CollectionUpdateRequest(null, null, null, null, null)).isEmpty());
            assertFalse(validator.validate(new CollectionVersionRequest(null)).isEmpty());
            assertFalse(validator.validate(new CollectionVersionRequest(-1L)).isEmpty());
        }
    }
}
