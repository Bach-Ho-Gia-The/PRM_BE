package com.structure.springboot.infrastructure.external.cloudinary;

import com.cloudinary.Cloudinary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({
        CloudinaryProperties.class,
        CloudinaryQuotaProperties.class
})
public class CloudinaryConfig{

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties properties) {
        Map<String, Object> options = new HashMap<>();

        options.put("cloud_name", properties.cloudName());
        options.put("api_key", properties.apiKey());
        options.put("api_secret", properties.apiSecret());
        options.put("secure", true);

        // cloudinary-http5 2.4.0 sử dụng đơn vị giây tại cấu hình SDK.
        options.put("timeout", (int) properties.timeout().toSeconds());

        return new Cloudinary(options);
    }
}
