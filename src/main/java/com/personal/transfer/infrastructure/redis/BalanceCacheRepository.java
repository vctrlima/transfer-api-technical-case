package com.personal.transfer.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.transfer.application.dto.BalanceResult;
import com.personal.transfer.application.ports.out.BalanceCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BalanceCacheRepository implements BalanceCachePort {

    private static final String KEY_PREFIX = "balance::";
    private static final Duration TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void put(String accountId, BalanceResult balance) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + accountId, objectMapper.writeValueAsString(balance), TTL);
            log.debug("Balance cached for accountId={}", accountId);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize balance for cache, accountId={}", accountId);
        }
    }

    @Override
    public Optional<BalanceResult> get(String accountId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + accountId))
                .flatMap(value -> {
                    try {
                        return Optional.of(objectMapper.readValue(value, BalanceResult.class));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cached balance for accountId={}", accountId);
                        return Optional.empty();
                    }
                });
    }

    /**
     * Evicts multiple cache entries in a single DEL command (one Redis round-trip).
     */
    @Override
    public void evictAll(String... accountIds) {
        Set<String> keys = new java.util.HashSet<>();
        for (String id : accountIds) {
            keys.add(KEY_PREFIX + id);
        }
        redisTemplate.delete(keys);
        log.debug("Balance cache evicted for accounts={}", (Object) accountIds);
    }
}
