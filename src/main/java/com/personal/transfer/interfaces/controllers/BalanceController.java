package com.personal.transfer.interfaces.controllers;

import com.personal.transfer.application.dto.BalanceResult;
import com.personal.transfer.application.query.GetBalanceQueryUseCase;
import com.personal.transfer.interfaces.dto.BalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
public class BalanceController {

    private final GetBalanceQueryUseCase getBalanceQueryUseCase;

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        log.info("Balance query for accountId={}", accountId);
        BalanceResult result = getBalanceQueryUseCase.execute(accountId);
        return ResponseEntity.ok(toResponse(result));
    }

    private BalanceResponse toResponse(BalanceResult result) {
        return new BalanceResponse(
                result.accountId(),
                result.customerName(),
                result.balance(),
                result.availableLimit(),
                result.dailyLimitUsed(),
                result.dailyLimitRemaining(),
                result.updatedAt()
        );
    }
}
