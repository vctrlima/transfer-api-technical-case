package com.personal.transfer.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived Redis cache for Cadastro API customer responses.
 *
 * <p>FetchCustomer is the slowest step in the SAGA (~60ms HTTP).
 * Caching with a 60-second TTL converts repeat requests from
 * the same account into a sub-millisecond Redis lookup.
 *
 * <p>A short TTL (60s) ensures that customer status changes
 * (e.g., account blocked) propagate within a minute without
 * impacting correctness of the critical account-validation step
 * (ValidateAccountStep checks account status from the DB).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CustomerCacheRepository {

    private static final String KEY_PREFIX = "customer::";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public Optional<String> get(String accountId) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + accountId);
            if (value != null) {
                log.info("Customer cache HIT for accountId={}", accountId);
                return Optional.of(value);
            }
            log.info("Customer cache MISS for accountId={}", accountId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Customer cache GET failed for accountId={}: {}", accountId, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String accountId, String customerJson) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + accountId, customerJson, TTL);
            log.debug("Customer cached for accountId={}, ttl={}s", accountId, TTL.getSeconds());
        } catch (Exception e) {
            log.warn("Customer cache PUT failed for accountId={}: {}", accountId, e.getMessage());
        }
    }
}
