package com.personal.transfer.domain.entities;

import com.personal.transfer.domain.exceptions.AccountInactiveException;
import com.personal.transfer.domain.exceptions.InsufficientBalanceException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    private String id;
    private AccountStatus status;
    private BigDecimal balance;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(this.status);
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }

    public void ensureActive() {
        if (!isActive()) {
            throw new AccountInactiveException(id);
        }
    }

    public void ensureSufficientBalance(BigDecimal amount) {
        if (!hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException(balance, amount);
        }
    }

    public void debit(BigDecimal amount) {
        ensureActive();
        ensureSufficientBalance(amount);
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        ensureActive();
        this.balance = this.balance.add(amount);
    }
}
