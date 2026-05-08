package com.personal.transfer.interfaces.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.transfer.application.usecases.TransferUseCase;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.valueobjects.Money;
import com.personal.transfer.infrastructure.persistence.TransferRepository;
import com.personal.transfer.infrastructure.redis.IdempotencyRepository;
import com.personal.transfer.interfaces.dto.ErrorResponse;
import com.personal.transfer.interfaces.dto.TransferRequest;
import com.personal.transfer.interfaces.dto.TransferResponse;
import jakarta.persistence.EntityNotFoundException;
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
    private final TransferRepository transferRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of("MISSING_IDEMPOTENCY_KEY", "Header 'Idempotency-Key' is required"));
        }

        var cached = idempotencyRepository.findByKey(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Idempotent response returned for key={}", idempotencyKey);
            try {
                TransferResponse response = objectMapper.readValue(cached.get(), TransferResponse.class);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached idempotency response: {}", e.getMessage());
            }
        }

        Transfer transfer = transferUseCase.execute(
                request.originAccountId(),
                request.destinationAccountId(),
                new Money(request.amount()),
                idempotencyKey,
                request.description()
        );

        TransferResponse response = new TransferResponse(
                transfer.getId(),
                transfer.getStatus(),
                transfer.getAmount(),
                transfer.getOriginAccountId(),
                transfer.getDestinationAccountId(),
                transfer.getCreatedAt()
        );

        try {
            idempotencyRepository.save(idempotencyKey, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.error("Failed to cache idempotency response for key={}: {}", idempotencyKey, e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable String transferId) {
        log.info("Transfer status query for transferId={}", transferId);
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found: " + transferId));

        TransferResponse response = new TransferResponse(
                transfer.getId(),
                transfer.getStatus(),
                transfer.getAmount(),
                transfer.getOriginAccountId(),
                transfer.getDestinationAccountId(),
                transfer.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }
}
