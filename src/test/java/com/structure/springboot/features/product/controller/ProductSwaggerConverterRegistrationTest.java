package com.structure.springboot.features.product.controller;

import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductSwaggerConverterRegistrationTest {
    @Configuration
    @EnableWebMvc
    @Import({ProductController.class, ProductMetadataMultipartConfig.class})
    static class MvcConfig {
        @Bean ProductService productService() { return mock(ProductService.class); }
        @Bean JsonMapper jsonMapper() { return new JsonMapper(); }
    }

    @Test
    void webMvcRegistersProductConverterWithoutManualMockMvcInjection() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(MvcConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var json = """
                    {"name":"Áo mẫu","sku":"SKU-01","price":99999,"categoryId":"%s","status":"HIDDEN"}
                    """.formatted(UUID.randomUUID());
            var metadata = new MockPart("metadata", json.getBytes(StandardCharsets.UTF_8));
            metadata.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            var one = new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1});
            var two = new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[]{2});
            mvc.perform(multipart("/products/admin").part(metadata).file(one).file(two)
                            .queryParam("thumbnailIndex", "0"))
                    .andExpect(status().isCreated());
            var request = org.mockito.ArgumentCaptor.forClass(ProductRequest.class);
            verify(context.getBean(ProductService.class)).createProductWithImages(request.capture(), anyList(), eq(0));
            org.junit.jupiter.api.Assertions.assertEquals("Áo mẫu", request.getValue().name());
        }
    }
}
