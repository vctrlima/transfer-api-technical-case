package com.personal.transfer.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.transfer.application.dto.TransferResult;
import com.personal.transfer.application.ports.out.IdempotencyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class IdempotencyRepository implements IdempotencyPort {

    private static final String KEY_PREFIX = "idempotency::";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<TransferResult> findTransferResult(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, TransferResult.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize idempotency response for key={}", idempotencyKey);
            return Optional.empty();
        }
    }

    @Override
    public void saveTransferResult(String idempotencyKey, TransferResult response) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        try {
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(response), TTL);
            log.debug("Idempotency key saved: {}", idempotencyKey);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize idempotency response for key={}", idempotencyKey);
        }
    }
}
