package com.personal.transfer.domain.exceptions;

public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(String transferId) {
        super("Transfer not found: " + transferId);
    }
}
