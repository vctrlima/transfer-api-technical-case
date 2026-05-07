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
public class BalanceCacheRepository {

    private static final String KEY_PREFIX = "balance::";
    private static final Duration TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;

    public void put(String accountId, String balanceJson) {
        redisTemplate.opsForValue().set(KEY_PREFIX + accountId, balanceJson, TTL);
        log.debug("Balance cached for accountId={}", accountId);
    }

    public Optional<String> get(String accountId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + accountId));
    }

    public void evict(String accountId) {
        redisTemplate.delete(KEY_PREFIX + accountId);
    }
}
