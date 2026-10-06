package com.capstone.executor.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Handles routing of failed messages to the dead-letter queue.
 * Preserves the original message and includes the failure reason in headers.
 */
@Component
public class DeadLetterQueueHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(DeadLetterQueueHandler.class);

    public static final String DLQ_TOPIC = "orders.DLT";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DeadLetterQueueHandler(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends the original message to the dead-letter queue.
     *
     * The original message is preserved as the Kafka value.
     * The failure reason is stored in the x-failure-reason header.
     *
     * @param originalMessage original Kafka message
     * @param failureReason reason for the failure
     * @param originalTopic original Kafka topic
     */
    public void sendToDeadLetterQueue(
            String originalMessage,
            String failureReason,
            String originalTopic) {

        try {
            Message<String> dlqMessage = MessageBuilder
                    .withPayload(originalMessage)
                    .setHeader(KafkaHeaders.TOPIC, DLQ_TOPIC)
                    .setHeader("x-failure-reason", failureReason)
                    .setHeader("x-original-topic", originalTopic)
                    .setHeader(
                            "x-dlq-timestamp",
                            System.currentTimeMillis()
                    )
                    .build();

            // Wait for Kafka confirmation.
            // If publishing fails, do not acknowledge the original message.
            kafkaTemplate.send(dlqMessage).get();

            logger.info(
                    "Message sent to DLQ: topic={}, reason={}",
                    DLQ_TOPIC,
                    failureReason
            );

        } catch (Exception e) {
            logger.error(
                    "Failed to send message to DLQ: {}",
                    e.getMessage(),
                    e
            );

          
            throw new IllegalStateException(
                    "DLQ_PUBLISH_FAILED",
                    e
            );
        }
    }
}