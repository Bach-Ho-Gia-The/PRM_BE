package com.structure.springboot.features.category.controller;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.category.dto.response.CategoryPublicResponse;
import com.structure.springboot.features.category.dto.response.CategoryResponse;
import com.structure.springboot.features.category.service.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    private final CategoryService service = mock(CategoryService.class);
    private final CategoryController controller = new CategoryController(service);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void oneControllerHasFiveGuardedAdminRoutesAndThreeDistinctPublicGets() {
        assertEquals("/categories", CategoryController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertNull(CategoryController.class.getAnnotation(PreAuthorize.class));
        Map<String, String> publicPaths = Map.of(
                "getPublicCategoryById", "/id/{id}",
                "getPublicCategoryBySlug", "/slug/{slug}",
                "getPublicCategories", "");
        int admin = 0;
        int publicGets = 0;
        for (Method method : CategoryController.class.getDeclaredMethods()) {
            String path = null;
            if (method.isAnnotationPresent(GetMapping.class)) {
                path = String.join("", method.getAnnotation(GetMapping.class).value());
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                path = String.join("", method.getAnnotation(PostMapping.class).value());
            } else if (method.isAnnotationPresent(PatchMapping.class)) {
                path = String.join("", method.getAnnotation(PatchMapping.class).value());
            } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                path = String.join("", method.getAnnotation(DeleteMapping.class).value());
            }
            if (path == null) {
                continue;
            }
            if (path.startsWith("/admin")) {
                admin++;
                assertEquals("hasRole('ADMIN')", method.getAnnotation(PreAuthorize.class).value());
            } else {
                publicGets++;
                assertEquals(publicPaths.get(method.getName()), path);
                assertNull(method.getAnnotation(PreAuthorize.class));
                assertTrue(method.isAnnotationPresent(GetMapping.class));
            }
        }
        assertEquals(5, admin);
        assertEquals(3, publicGets);
    }

    @Test
    void publicAndAdminPathsDelegateToDifferentServiceMethods() {
        var pageable = PageRequest.of(0, 2);
        var id = UUID.randomUUID();
        PageResponse<CategoryPublicResponse> publicPage = new PageResponse<>(List.of(), 0, 2, 0, 0, true, true);
        PageResponse<CategoryResponse> adminPage = new PageResponse<>(List.of(), 0, 2, 0, 0, true, true);
        when(service.getPublicCategories(pageable)).thenReturn(publicPage);
        when(service.getAdminCategories(pageable)).thenReturn(adminPage);
        when(request.getRequestURI()).thenReturn("/categories");

        assertEquals(publicPage, controller.getPublicCategories(pageable, request).getBody().getResult());
        assertEquals(adminPage, controller.getAdminCategories(pageable, request).getBody().getResult());
        verify(service).getPublicCategories(pageable);
        verify(service).getAdminCategories(pageable);

        CategoryPublicResponse category = new CategoryPublicResponse(id, "Category", "category");
        when(service.getPublicCategoryById(id)).thenReturn(category);
        when(service.getPublicCategoryBySlug("category")).thenReturn(category);
        assertEquals(category, controller.getPublicCategoryById(id, request).getBody().getResult());
        assertEquals(category, controller.getPublicCategoryBySlug("category", request).getBody().getResult());
    }
}
