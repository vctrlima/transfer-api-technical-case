package com.personal.transfer.infrastructure.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.transfer.application.dto.BacenNotification;
import com.personal.transfer.application.dto.BacenTransferEvent;
import com.personal.transfer.application.ports.out.BacenNotificationPort;
import com.personal.transfer.application.ports.out.TransferPort;
import com.personal.transfer.domain.entities.Transfer;
import com.personal.transfer.domain.entities.TransferStatus;
import com.personal.transfer.infrastructure.adapters.BacenRateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "aws.sqs.consumer.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BacenEventConsumer {

    private final SqsClient sqsClient;
    private final BacenNotificationPort bacenNotificationPort;
    private final TransferPort transferPort;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

        for (Message message : messages) {
            processMessage(message);
        }
    }

    private void processMessage(Message message) {
        try {
            BacenTransferEvent event = objectMapper.readValue(message.body(), BacenTransferEvent.class);
            log.info("Processing BACEN notification for transferId={}", event.transferId());

            Optional<Transfer> existingTransfer = transferPort.findById(event.transferId());
            if (existingTransfer.isPresent() && TransferStatus.COMPLETED.equals(existingTransfer.get().getStatus())) {
                log.info("Transfer already completed (idempotent skip) transferId={}", event.transferId());
                deleteMessage(message);
                return;
            }

            BacenNotification notifyRequest = new BacenNotification(
                    event.transferId(),
                    event.originAccountId(),
                    event.destinationAccountId(),
                    event.amount(),
                    event.occurredAt()
            );

            bacenNotificationPort.notify(notifyRequest);

            updateTransferStatus(event.transferId());
            deleteMessage(message);
            log.info("BACEN notification successful, transferId={} marked COMPLETED", event.transferId());

        } catch (BacenRateLimitException e) {
            log.warn("BACEN rate limit, message will be requeued: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process BACEN event, message will be requeued: {}", e.getMessage(), e);
        }
    }

    private void deleteMessage(Message message) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();
        sqsClient.deleteMessage(deleteRequest);
    }

    private void updateTransferStatus(String transferId) {
        transferPort.findById(transferId).ifPresent(transfer -> {
            transfer.markAs(TransferStatus.COMPLETED);
            transferPort.save(transfer);
        });
    }
}
