package com.personal.transfer.domain.exceptions;

public class AccountInactiveException extends RuntimeException {

    public AccountInactiveException(String accountId) {
        super("Account " + accountId + " is not active");
    }
}
