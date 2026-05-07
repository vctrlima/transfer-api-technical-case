package com.personal.transfer.application.usecases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.infrastructure.persistence.AccountRepository;
import com.personal.transfer.infrastructure.redis.BalanceCacheRepository;
import com.personal.transfer.infrastructure.redis.DailyLimitRepository;
import com.personal.transfer.interfaces.dto.BalanceResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceUseCase {

    private final AccountRepository accountRepository;
    private final BalanceCacheRepository balanceCacheRepository;
    private final DailyLimitRepository dailyLimitRepository;
    private final ObjectMapper objectMapper;

    @Value("${transfer.daily-limit:1000.00}")
    private BigDecimal dailyLimit;

    public BalanceResponse getBalance(String accountId) {
        var cached = balanceCacheRepository.get(accountId);
        if (cached.isPresent()) {
            log.debug("Balance cache hit for accountId={}", accountId);
            try {
                return objectMapper.readValue(cached.get(), BalanceResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached balance for accountId={}, fetching from DB", accountId);
            }
        }

        log.debug("Balance cache miss for accountId={}, fetching from DB", accountId);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));

        BigDecimal dailyLimitUsed = dailyLimitRepository.getAccumulated(accountId);
        BigDecimal dailyLimitRemaining = dailyLimit.subtract(dailyLimitUsed).max(BigDecimal.ZERO);

        BalanceResponse response = new BalanceResponse(
                account.getId(),
                account.getBalance(),
                dailyLimit,
                dailyLimitUsed,
                dailyLimitRemaining,
                account.getUpdatedAt()
        );

        try {
            balanceCacheRepository.put(accountId, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache balance for accountId={}", accountId);
        }

        return response;
    }
}
