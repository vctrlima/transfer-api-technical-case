package com.personal.transfer.domain.valueobjects;

public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be blank");
        }
    }
}
