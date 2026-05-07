package com.personal.transfer.interfaces.controllers;

import com.personal.transfer.application.usecases.BalanceUseCase;
import com.personal.transfer.interfaces.dto.BalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceUseCase balanceUseCase;

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        log.info("Balance query for accountId={}", accountId);
        BalanceResponse response = balanceUseCase.getBalance(accountId);
        return ResponseEntity.ok(response);
    }
}
