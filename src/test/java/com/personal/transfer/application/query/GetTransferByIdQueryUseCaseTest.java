package com.personal.transfer.application.query;

import com.personal.transfer.application.ports.out.TransferPort;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.domain.exceptions.TransferNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetTransferByIdQueryUseCase")
class GetTransferByIdQueryUseCaseTest {

    @Mock
    private TransferPort transferPort;

    @InjectMocks
    private GetTransferByIdQueryUseCase getTransferByIdQueryUseCase;

    @Test
    @DisplayName("execute → retorna TransferResult quando transferência existe")
    void givenExistingTransfer_whenExecute_thenReturnsTransferResult() {
        LocalDateTime createdAt = LocalDateTime.now();
        Transfer transfer = Transfer.builder()
                .id("transfer-001")
                .originAccountId("acc-origin")
                .destinationAccountId("acc-dest")
                .amount(new BigDecimal("200.00"))
                .status(TransferStatus.PROCESSING)
                .idempotencyKey("idem-key")
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        when(transferPort.findById("transfer-001")).thenReturn(Optional.of(transfer));

        var result = getTransferByIdQueryUseCase.execute("transfer-001");

        assertThat(result.transferId()).isEqualTo("transfer-001");
        assertThat(result.status()).isEqualTo(TransferStatus.PROCESSING);
        assertThat(result.amount()).isEqualByComparingTo("200.00");
        assertThat(result.originAccountId()).isEqualTo("acc-origin");
        assertThat(result.destinationAccountId()).isEqualTo("acc-dest");
        assertThat(result.createdAt()).isEqualTo(createdAt);
        verify(transferPort).findById("transfer-001");
    }

    @Test
    @DisplayName("execute → lança TransferNotFoundException quando transferência não existe")
    void givenMissingTransfer_whenExecute_thenThrowsTransferNotFoundException() {
        when(transferPort.findById("missing-transfer")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getTransferByIdQueryUseCase.execute("missing-transfer"))
                .isInstanceOf(TransferNotFoundException.class)
                .hasMessageContaining("missing-transfer");
    }
}
