package com.personal.transfer.application.query;

import com.personal.transfer.application.dto.BalanceResult;
import com.personal.transfer.application.usecases.BalanceUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetBalanceQueryUseCase {

    private final BalanceUseCase balanceUseCase;

    public BalanceResult execute(String accountId) {
        return balanceUseCase.getBalance(accountId);
    }
}
