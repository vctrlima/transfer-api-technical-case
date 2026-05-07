package com.personal.transfer.infrastructure.adapters;

public class BacenRateLimitException extends RuntimeException {

    public BacenRateLimitException(String message) {
        super(message);
    }
}
