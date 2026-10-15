package com.structure.springboot.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // Tắt tính năng ghi ngày tháng dưới dạng số (epoch millis)
            builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);

            // Bỏ qua các trường không xác định trong JSON body
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            // Đăng ký module tùy chỉnh để trim String
//            SimpleModule stringTrimModule = new SimpleModule("StringTrimModule");
//            stringTrimModule.addDeserializer(String.class, new StringTrimDeserializer());
//            builder.addModule(stringTrimModule);
        };
    }

    /**
     * [GLOBAL] Trim khoảng trắng cho MỌI field String.
     * "  abc  " → "abc"
     * "   "     → null
     */
    public static class StringTrimDeserializer extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            String value = p.getValueAsString();
            if (value == null) return null;
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    /**
     * [CỤC BỘ] Trim + UpperCase, dùng qua annotation trên DTO:
     *
     *   @JsonDeserialize(using = JacksonConfig.UpperCaseTrimDeserializer.class)
     *   String roleName;
     *
     * Import phải dùng Jackson 3:
     *   import tools.jackson.databind.annotation.JsonDeserialize;
     *
     * "  admin  " → "ADMIN"
     * "   "       → null
     */
    public static class UpperCaseTrimDeserializer extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            String value = p.getValueAsString();
            if (value == null) return null;
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed.toUpperCase();
        }
    }
}
