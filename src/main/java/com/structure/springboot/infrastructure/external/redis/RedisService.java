package com.structure.springboot.infrastructure.external.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;   // ✅ Jackson 3

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JsonMapper jsonMapper;   // ✅ JsonMapper thay ObjectMapper

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public <T> Optional<T> get(String key, Class<T> clazz) {
        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) return Optional.empty();

        if (clazz.isInstance(val)) {
            return Optional.of(clazz.cast(val));
        }

        try {
            return Optional.of(jsonMapper.convertValue(val, clazz));
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