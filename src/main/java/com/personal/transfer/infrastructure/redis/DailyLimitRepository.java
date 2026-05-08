package com.personal.transfer.infrastructure.redis;

import com.personal.transfer.application.ports.out.DailyLimitPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DailyLimitRepository implements DailyLimitPort {

    private static final String KEY_PREFIX = "limit::";
    private static final Duration TTL = Duration.ofHours(48);
    private static final long SCALE_FACTOR = 100L; /* in cents */

    private final StringRedisTemplate redisTemplate;

    /**
     * Atomically increments the daily limit counter and refreshes TTL.
     * Uses a Redis pipeline to send INCRBY + EXPIRE in a single round-trip
     * instead of two sequential commands.
     * Returns the new accumulated value in full currency units (e.g., BRL).
     */
    @Override
    public BigDecimal incrementAndGet(String accountId, BigDecimal amount) {
        String key = buildKey(accountId);
        long amountInCents = toCents(amount);

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            connection.stringCommands().incrBy(keyBytes, amountInCents);
            connection.keyCommands().expire(keyBytes, TTL.getSeconds());
            return null;
        });

        Long newValueInCents = (Long) results.get(0);
        log.debug("Daily limit incremented for accountId={}, newTotal={} cents", accountId, newValueInCents);
        return fromCents(newValueInCents);
    }

    /**
     * Decrements the daily limit counter (used in SAGA compensation).
     */
    @Override
    public void decrement(String accountId, BigDecimal amount) {
        String key = buildKey(accountId);
        long amountInCents = toCents(amount);
        redisTemplate.opsForValue().decrement(key, amountInCents);
        log.debug("Daily limit decremented (compensation) for accountId={}, amount={}", accountId, amount);
    }

    /**
     * Returns current accumulated value for today.
     */
    @Override
    public BigDecimal getAccumulated(String accountId) {
        String key = buildKey(accountId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return BigDecimal.ZERO;
        return fromCents(Long.parseLong(value));
    }

    private String buildKey(String accountId) {
        return KEY_PREFIX + accountId + "::" + LocalDate.now();
    }

    private long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY)
                .multiply(BigDecimal.valueOf(SCALE_FACTOR))
                .longValueExact();
    }

    private BigDecimal fromCents(Long cents) {
        if (cents == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(SCALE_FACTOR), 2, RoundingMode.UNNECESSARY);
    }
}
