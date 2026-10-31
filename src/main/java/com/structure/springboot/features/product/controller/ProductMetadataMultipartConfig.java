package com.structure.springboot.features.product.controller;

import com.structure.springboot.features.product.dto.request.ProductRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

/** Read Swagger's plain-text JSON metadata without changing other request DTOs or JSON clients. */
@Configuration
public class ProductMetadataMultipartConfig implements WebMvcConfigurer {
    private final JsonMapper mapper;

    public ProductMetadataMultipartConfig(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new PlainProductMetadataConverter(mapper));
    }

    static final class PlainProductMetadataConverter extends AbstractHttpMessageConverter<ProductRequest> {
        private final JsonMapper mapper;

        PlainProductMetadataConverter(JsonMapper mapper) {
            super(MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM);
            this.mapper = mapper;
        }

        @Override
        protected boolean supports(Class<?> type) {
            return type == ProductRequest.class;
        }

        @Override
        public boolean canWrite(Class<?> type, MediaType mediaType) {
            return false;
        }

        @Override
        protected ProductRequest readInternal(Class<? extends ProductRequest> type, HttpInputMessage input)
                throws IOException {
            try {
                ProductRequest metadata = mapper.readValue(input.getBody(), ProductRequest.class);
                if (metadata == null) {
                    throw new HttpMessageNotReadableException("Product metadata must be a JSON object", input);
                }
                return metadata;
            } catch (JacksonException exception) {
                throw new HttpMessageNotReadableException("Invalid product metadata JSON", exception, input);
            }
        }

        @Override
        protected void writeInternal(ProductRequest value, HttpOutputMessage output) {
            throw new UnsupportedOperationException("Read-only converter");
        }
    }
}
