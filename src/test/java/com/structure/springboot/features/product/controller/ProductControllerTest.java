package com.structure.springboot.features.product.controller;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.dto.response.ProductResponse;
import com.structure.springboot.features.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductControllerTest {

    private final ProductService service = mock(ProductService.class);
    private final ProductController controller = new ProductController(service);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void oneControllerHasFourteenGuardedAdminRoutesAndThreePublicGets() {
        assertEquals("/products", ProductController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertNull(ProductController.class.getAnnotation(PreAuthorize.class));
        int admin = 0;
        int publicGets = 0;
        for (Method method : ProductController.class.getDeclaredMethods()) {
            String path = null;
            if (method.isAnnotationPresent(GetMapping.class)) {
                path = String.join("", method.getAnnotation(GetMapping.class).value());
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                path = String.join("", method.getAnnotation(PostMapping.class).value());
            } else if (method.isAnnotationPresent(PatchMapping.class)) {
                path = String.join("", method.getAnnotation(PatchMapping.class).value());
            } else if (method.isAnnotationPresent(PutMapping.class)) {
                path = String.join("", method.getAnnotation(PutMapping.class).value());
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
                assertNull(method.getAnnotation(PreAuthorize.class));
                if (method.getName().equals("getProductById")) {
                    assertEquals("/id/{id}", path);
                } else if (method.getName().equals("getProductBySlug")) {
                    assertEquals("/slug/{slug}", path);
                } else {
                    assertEquals("", path);
                    assertEquals("getAllProducts", method.getName());
                }
            }
        }
        assertEquals(14, admin); // 10 cũ + 4 endpoint variant
        assertEquals(3, publicGets);
    }

    @Test
    void multipartGalleryUploadReturnsDetailAndUsesAdminOnlyService() {
        UUID id = UUID.randomUUID();
        var file = new MockMultipartFile("file", "new.png", "image/png", new byte[]{1});
        var detail = mock(ProductDetailResponse.class);
        when(service.addImage(id, file, true)).thenReturn(detail);
        when(request.getRequestURI()).thenReturn("/products/admin/" + id + "/images");

        var result = controller.addImage(id, file, true, request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(detail, result.getBody().getResult());
        verify(service).addImage(id, file, true);
    }

    @Test
    void publicListDelegatesFiltersAndAdminListUsesSeparateServiceMethod() {
        ProductFilter filters = new ProductFilter("ao", "sunset", null, null, null, null);
        var pageable = PageRequest.of(0, 2);
        PageResponse<ProductResponse> publicPage = new PageResponse<>(List.of(), 0, 2, 0, 0, true, true);
        when(service.getAllProducts(filters, pageable)).thenReturn(publicPage);
        when(request.getRequestURI()).thenReturn("/products");
        var response = controller.getAllProducts(filters, pageable, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(publicPage, response.getBody().getResult());
        verify(service).getAllProducts(filters, pageable);

        when(service.getAdminProducts(pageable)).thenReturn(publicPage);
        when(request.getRequestURI()).thenReturn("/products/admin");
        assertEquals(publicPage, controller.getAdminProducts(pageable, request).getBody().getResult());
        verify(service).getAdminProducts(pageable);
    }
}
