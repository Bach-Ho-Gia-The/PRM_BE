package com.structure.springboot.features.collection.controller;

import com.structure.springboot.features.collection.dto.request.CollectionProductsRequest;
import com.structure.springboot.features.collection.dto.request.CollectionUpdateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.dto.response.CollectionDetailResponse;
import com.structure.springboot.features.collection.service.CollectionService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionControllerTest {

    private final CollectionService service = mock(CollectionService.class);
    private final CollectionController controller = new CollectionController(service);
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);

    @Test
    void allCurrentMethodsShareCollectionsPathAndRequireAdminIndividually() {
        assertEquals("/collections", CollectionController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertNull(CollectionController.class.getAnnotation(PreAuthorize.class));

        int endpoints = 0;
        for (Method method : CollectionController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class)
                    || method.isAnnotationPresent(PostMapping.class)
                    || method.isAnnotationPresent(PutMapping.class)
                    || method.isAnnotationPresent(PatchMapping.class)
                    || method.isAnnotationPresent(DeleteMapping.class)) {
                endpoints++;
                assertEquals("hasRole('ADMIN')", method.getAnnotation(PreAuthorize.class).value());
            }
        }
        assertEquals(8, endpoints);
    }

    @Test
    void putUsesNumericVersionInDtoAndReturnsNewVersionInJson() {
        UUID id = UUID.randomUUID();
        CollectionProductsRequest body = new CollectionProductsRequest(4L, List.of(UUID.randomUUID()));
        CollectionDetailResponse updated = new CollectionDetailResponse(
                id, "Summer", "summer", null, RecordStatus.HIDDEN, 5L, List.of(), 0L, null, null);
        when(service.replaceProducts(id, body)).thenReturn(updated);
        when(httpRequest.getRequestURI()).thenReturn("/collections/" + id + "/products");

        var response = controller.replaceProducts(id, body, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getHeaders().getETag());
        assertEquals(5L, response.getBody().getResult().version());
        verify(service).replaceProducts(id, body);
    }

    @Test
    void deleteUsesNumericQueryParamWithoutRequestBody() {
        UUID id = UUID.randomUUID();
        when(httpRequest.getRequestURI()).thenReturn("/collections/" + id);

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
