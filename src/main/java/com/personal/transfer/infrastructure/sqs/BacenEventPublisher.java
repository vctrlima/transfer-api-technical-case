package com.personal.transfer.infrastructure.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.transfer.domain.exceptions.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class BacenEventPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    public void publish(BacenTransferEvent event) {
        try {
            String messageBody = objectMapper.writeValueAsString(event);
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageGroupId("transfer-" + event.transferId())
                    .messageDeduplicationId(event.transferId())
                    .build();

            sqsClient.sendMessage(request);
            log.info("Event published to SQS for transferId={}", event.transferId());
        } catch (JsonProcessingException e) {
            throw new ExternalServiceException("Failed to serialize BACEN event: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExternalServiceException("Failed to publish event to SQS: " + e.getMessage(), e);
        }
    }
}
