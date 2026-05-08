package com.personal.transfer.infrastructure.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.infrastructure.adapters.BacenApiPort;
import com.personal.transfer.infrastructure.adapters.BacenRateLimitException;
import com.personal.transfer.infrastructure.adapters.dto.BacenNotifyRequest;
import com.personal.transfer.infrastructure.persistence.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BacenEventConsumer — mecanismo de retry via SQS (RN-429)")
class BacenEventConsumerTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private BacenApiPort bacenApiPort;

    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private BacenEventConsumer bacenEventConsumer;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/bacen-events";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bacenEventConsumer, "queueUrl", QUEUE_URL);
        ReflectionTestUtils.setField(bacenEventConsumer, "objectMapper", objectMapper);
    }

    private Message buildMessage(String transferId) throws Exception {
        BacenTransferEvent event = new BacenTransferEvent(
                transferId, "acc-origin-001", "acc-dest-001",
                new BigDecimal("200.00"), LocalDateTime.now()
        );
        return Message.builder()
                .body(objectMapper.writeValueAsString(event))
                .receiptHandle("receipt-handle-" + transferId)
                .build();
    }

    private void stubReceiveMessage(Message... messages) {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(messages)).build());
    }

    @Nested
    @DisplayName("Fluxo de sucesso")
    class SuccessFlow {

        @Test
        @DisplayName("notificação bem-sucedida → mensagem deletada e transfer marcado COMPLETED")
        void givenSuccessfulNotification_whenConsume_thenMessageDeletedAndTransferCompleted() throws Exception {
            Message message = buildMessage("t-001");
            stubReceiveMessage(message);

            Transfer transfer = Transfer.builder()
                    .id("t-001").status(TransferStatus.PROCESSING).build();
            when(transferRepository.findById("t-001")).thenReturn(Optional.of(transfer));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(bacenApiPort).notify(any(BacenNotifyRequest.class));

            bacenEventConsumer.consume();

            verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
            ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
            verify(transferRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        }

        @Test
        @DisplayName("transfer já COMPLETED (idempotência) → mensagem deletada sem chamar BACEN")
        void givenAlreadyCompletedTransfer_whenConsume_thenSkipNotificationAndDeleteMessage() throws Exception {
            Message message = buildMessage("t-completed");
            stubReceiveMessage(message);

            Transfer completedTransfer = Transfer.builder()
                    .id("t-completed").status(TransferStatus.COMPLETED).build();
            when(transferRepository.findById("t-completed")).thenReturn(Optional.of(completedTransfer));

            bacenEventConsumer.consume();

            verifyNoInteractions(bacenApiPort);
            verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
        }
    }

    @Nested
    @DisplayName("Mecanismo de retry via SQS para 429")
    class RateLimitRetry {

        @Test
        @DisplayName("BACEN retorna 429 (BacenRateLimitException) → mensagem NÃO deletada → re-enfileirada automaticamente pelo SQS")
        void givenBacenRateLimitException_whenConsume_thenMessageNotDeleted() throws Exception {
            Message message = buildMessage("t-rate-limited");
            stubReceiveMessage(message);

            when(transferRepository.findById("t-rate-limited")).thenReturn(Optional.empty());
            doThrow(new BacenRateLimitException("Rate limit exceeded"))
                    .when(bacenApiPort).notify(any(BacenNotifyRequest.class));

            bacenEventConsumer.consume();

            verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        }

        @Test
        @DisplayName("erro genérico → mensagem NÃO deletada → re-enfileirada para nova tentativa")
        void givenGenericException_whenConsume_thenMessageNotDeleted() throws Exception {
            Message message = buildMessage("t-error");
            stubReceiveMessage(message);

            when(transferRepository.findById("t-error")).thenReturn(Optional.empty());
            doThrow(new RuntimeException("Connection refused"))
                    .when(bacenApiPort).notify(any(BacenNotifyRequest.class));

            bacenEventConsumer.consume();

            verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        }
    }
}

