package com.personal.transfer.application.query;

import com.personal.transfer.application.dto.TransferResult;
import com.personal.transfer.application.ports.out.TransferPort;
import com.personal.transfer.domain.exceptions.TransferNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetTransferByIdQueryUseCase {

    private final TransferPort transferPort;

    public TransferResult execute(String transferId) {
        log.info("Transfer status query for transferId={}", transferId);
        return transferPort.findById(transferId)
                .map(TransferResult::from)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }
}
