package com.personal.transfer.application.usecases;

import com.personal.transfer.application.dto.BalanceResult;
import com.personal.transfer.application.dto.CustomerInfo;
import com.personal.transfer.application.ports.out.AccountPort;
import com.personal.transfer.application.ports.out.BalanceCachePort;
import com.personal.transfer.application.ports.out.CustomerGateway;
import com.personal.transfer.application.ports.out.DailyLimitPort;
import com.personal.transfer.domain.entities.Account;
import com.personal.transfer.domain.exceptions.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceUseCase {

    private final AccountPort accountPort;
    private final BalanceCachePort balanceCachePort;
    private final DailyLimitPort dailyLimitPort;
    private final CustomerGateway customerGateway;

    @Value("${transfer.daily-limit:1000.00}")
    private BigDecimal dailyLimit;

    public BalanceResult getBalance(String accountId) {
        var cached = balanceCachePort.get(accountId);
        if (cached.isPresent()) {
            log.debug("Balance cache hit for accountId={}", accountId);
            return cached.get();
        }

        log.debug("Balance cache miss for accountId={}, fetching from DB", accountId);
        Account account = accountPort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.ensureActive();

        CustomerInfo customer = customerGateway.fetchCustomer(accountId);

        BigDecimal dailyLimitUsed = dailyLimitPort.getAccumulated(accountId);
        BigDecimal dailyLimitRemaining = dailyLimit.subtract(dailyLimitUsed).max(BigDecimal.ZERO);

        BalanceResult response = new BalanceResult(
                account.getId(),
                customer.name(),
                account.getBalance(),
                dailyLimit,
                dailyLimitUsed,
                dailyLimitRemaining,
                account.getUpdatedAt()
        );

        balanceCachePort.put(accountId, response);

        return response;
    }
}
