package com.capstone.executor.service;

import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.TradeEventPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publishes ORDER_FILLED and ORDER_REJECTED events
 * to the trade-events Kafka topic.
 */
@Component
public class TradeEventKafkaPublisher
        implements TradeEventPublisher {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    TradeEventKafkaPublisher.class
            );

    private static final String TRADE_EVENTS_TOPIC =
            "trade-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TradeEventKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {

        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(
            EventEnvelope<TradeEventPayload> event) {

        if (event == null) {

            logger.warn(
                    "Trade event is null - nothing to publish"
            );

            return;
        }

        try {

            /*
             * Convert event to JSON.
             */
            String eventJson =
                    objectMapper.writeValueAsString(event);

            /*
             * Key by accountId.
             *
             * This preserves per-account ordering.
             */
            String key =
                    event.getPayload()
                            .getAccountId()
                            .toString();

            logger.info(
                    "Sending trade event to Kafka: "
                            + "topic={} orderId={} eventType={} key={}",
                    TRADE_EVENTS_TOPIC,
                    event.getPayload().getOrderId(),
                    event.getEventType(),
                    key
            );

            /*
             * Send synchronously.
             *
             * .get() waits until Kafka acknowledges the message.
             */
            SendResult<String, String> result =
                    kafkaTemplate
                            .send(
                                    TRADE_EVENTS_TOPIC,
                                    key,
                                    eventJson
                            )
                            .get();

            RecordMetadata metadata =
                    result.getRecordMetadata();

            logger.info(
                    "Trade event published successfully: "
                            + "orderId={} eventType={} "
                            + "partition={} offset={}",
                    event.getPayload().getOrderId(),
                    event.getEventType(),
                    metadata.partition(),
                    metadata.offset()
            );

        } catch (Exception e) {

            logger.error(
                    "Failed to publish trade event: "
                            + "orderId={} eventType={} error={}",
                    event.getPayload().getOrderId(),
                    event.getEventType(),
                    e.getMessage(),
                    e
            );

            /*
             * Throw the exception back to OrderExecutionService.
             *
             * The Kafka consumer will therefore NOT acknowledge
             * the original orders message.
             */
            throw new RuntimeException(
                    "Trade event publish failed",
                    e
            );
        }
    }
}