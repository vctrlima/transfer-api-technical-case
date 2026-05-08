package com.personal.transfer.interfaces.controllers;

import com.personal.transfer.application.dto.TransferResult;
import com.personal.transfer.application.query.GetTransferByIdQueryUseCase;
import com.personal.transfer.application.usecases.TransferUseCase;
import com.personal.transfer.domain.valueobjects.Money;
import com.personal.transfer.interfaces.dto.ErrorResponse;
import com.personal.transfer.interfaces.dto.TransferRequest;
import com.personal.transfer.interfaces.dto.TransferResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferUseCase transferUseCase;
    private final GetTransferByIdQueryUseCase getTransferByIdQueryUseCase;

    @PostMapping
    public ResponseEntity<?> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of("MISSING_IDEMPOTENCY_KEY", "Header 'Idempotency-Key' is required"));
        }

        TransferResult result = transferUseCase.execute(
                request.originAccountId(),
                request.destinationAccountId(),
                new Money(request.amount()),
                idempotencyKey,
                request.description()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toResponse(result));
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable String transferId) {
        return ResponseEntity.ok(toResponse(getTransferByIdQueryUseCase.execute(transferId)));
    }

    private TransferResponse toResponse(TransferResult result) {
        return new TransferResponse(
                result.transferId(),
                result.status(),
                result.amount(),
                result.originAccountId(),
                result.destinationAccountId(),
                result.createdAt()
        );
    }
}
