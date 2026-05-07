package com.personal.transfer.interfaces.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(

        @NotBlank(message = "originAccountId is required")
        String originAccountId,

        @NotBlank(message = "destinationAccountId is required")
        String destinationAccountId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        @Digits(integer = 15, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        String description
) {
}
