package com.structure.springboot.infrastructure.external.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public <T> Optional<T> get(String key, Class<T> clazz) {
        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            return Optional.empty();
        }

        // Nếu đối tượng đã đúng kiểu (ví dụ String, Long...) thì cast luôn
        if (clazz.isInstance(val)) {
            return Optional.of(clazz.cast(val));
        }

        // Nếu Jackson trả về LinkedHashMap, convert an toàn sang đúng DTO mong muốn
        try {
            return Optional.of(objectMapper.convertValue(val, clazz));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void delete(Collection<String> keys) {
        redisTemplate.delete(keys);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }
}