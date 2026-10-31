package com.structure.springboot.features.product.controller;

import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductMultipartCreateMvcTest {
    private final ProductService service = mock(ProductService.class);
    private final JsonMapper mapper = new JsonMapper();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
            .setMessageConverters(new ProductMetadataMultipartConfig.PlainProductMetadataConverter(mapper),
                    new JacksonJsonHttpMessageConverter(mapper))
            .build();
    private final String json = """
            {"name":"Dress","sku":"S-01","price":20,"categoryId":"%s"}
            """.formatted(UUID.randomUUID());

    @Test
    void multipartBindsRepeatedFilesMetadataAndIndex() throws Exception {
        var metadata = new MockMultipartFile("metadata", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
        var one = new MockMultipartFile("images", "one.png", "image/png", new byte[]{1});
        var two = new MockMultipartFile("images", "two.png", "image/png", new byte[]{2});
        mvc.perform(multipart("/products/admin").file(metadata).file(one).file(two)
                        .param("thumbnailIndex", "1"))
                .andExpect(status().isCreated());
        var request = org.mockito.ArgumentCaptor.forClass(ProductRequest.class);
        @SuppressWarnings("unchecked") var files = org.mockito.ArgumentCaptor.forClass((Class<List<MultipartFile>>) (Class<?>) List.class);
        verify(service).createProductWithImages(request.capture(), files.capture(), eq(1));
        assertEquals("Dress", request.getValue().name());
        assertEquals(List.of("one.png", "two.png"), files.getValue().stream().map(MultipartFile::getOriginalFilename).toList());
    }

    @Test
    void multipartMetadataWithoutImagesAndLegacyJsonBothRouteCorrectly() throws Exception {
        var metadata = new MockMultipartFile("metadata", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/products/admin").file(metadata)).andExpect(status().isCreated());
        verify(service).createProductWithImages(any(ProductRequest.class), isNull(), isNull());
        mvc.perform(post("/products/admin").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        verify(service).createProduct(any(ProductRequest.class));
        mvc.perform(post("/products/admin").contentType("application/vnd.api+json").content(json))
                .andExpect(status().isCreated());
        verify(service, times(2)).createProduct(any(ProductRequest.class));
    }

    @Test
    void invalidOrMissingMetadataDoesNotCreateProduct() throws Exception {
        var metadata = new MockMultipartFile("metadata", "", "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/products/admin").file(metadata)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/products/admin")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test
    void swaggerPlainTextMetadataWithImagesSucceeds() throws Exception {
        var metadata = new MockPart("metadata", json.getBytes(StandardCharsets.UTF_8));
        metadata.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        var one = new MockMultipartFile("images", "one.jpg", "image/jpeg", new byte[]{1});
        var two = new MockMultipartFile("images", "two.jpg", "image/jpeg", new byte[]{2});
        mvc.perform(multipart("/products/admin").part(metadata).file(one).file(two)
                        .queryParam("thumbnailIndex", "0"))
                .andExpect(status().isCreated());
        verify(service).createProductWithImages(any(ProductRequest.class), anyList(), eq(0));
    }

    @Test
    void swaggerMetadataWithoutContentTypeAlsoSucceeds() throws Exception {
        var metadata = new MockPart("metadata", json.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/products/admin").part(metadata))
                .andExpect(status().isCreated());
        verify(service).createProductWithImages(any(ProductRequest.class), isNull(), isNull());
    }

    @Test
    void plainMetadataStillRequiresValidJsonAndRequiredFields() throws Exception {
        for (String value : List.of("{broken", "null", "{}")) {
            var metadata = new MockPart("metadata", value.getBytes(StandardCharsets.UTF_8));
            metadata.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            mvc.perform(multipart("/products/admin").part(metadata))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
