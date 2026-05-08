package com.personal.transfer.application.ports.out;

import com.personal.transfer.application.dto.BalanceResult;

import java.util.Optional;

public interface BalanceCachePort {

    Optional<BalanceResult> get(String accountId);

    void put(String accountId, BalanceResult balance);

    void evictAll(String... accountIds);
}
