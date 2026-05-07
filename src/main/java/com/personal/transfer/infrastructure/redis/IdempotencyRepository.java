package com.personal.transfer.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class IdempotencyRepository {

    private static final String KEY_PREFIX = "idempotency::";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public Optional<String> findByKey(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        String value = redisTemplate.opsForValue().get(redisKey);
        return Optional.ofNullable(value);
    }

    public void save(String idempotencyKey, String responseJson) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, responseJson, TTL);
        log.debug("Idempotency key saved: {}", idempotencyKey);
    }

    public boolean exists(String idempotencyKey) {
        return redisTemplate.hasKey(KEY_PREFIX + idempotencyKey);
    }
}
