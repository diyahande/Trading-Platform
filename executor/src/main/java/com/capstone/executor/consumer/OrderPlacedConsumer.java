package com.capstone.executor.consumer;

import com.capstone.executor.error.DeadLetterQueueHandler;
import com.capstone.executor.error.FailureClassifier;
import com.capstone.executor.error.RetryPolicy;
import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.OrderPlacedPayload;
import com.capstone.executor.service.OrderExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes ORDER_PLACED events from the orders topic.
 *
 * Permanent failures are sent directly to orders.DLT.
 * Transient failures are retried with bounded exponential backoff.
 */
@Component
public class OrderPlacedConsumer {

    private static final Logger logger =
            LoggerFactory.getLogger(OrderPlacedConsumer.class);

    private static final String ORDERS_TOPIC = "orders";

    private static final String EXPECTED_EVENT_TYPE =
            "ORDER_PLACED";

    private final OrderExecutionService executionService;

    private final ObjectMapper objectMapper;

    private final FailureClassifier failureClassifier;

    private final RetryPolicy retryPolicy;

    private final DeadLetterQueueHandler dlqHandler;

    public OrderPlacedConsumer(
            OrderExecutionService executionService,
            ObjectMapper objectMapper,
            FailureClassifier failureClassifier,
            RetryPolicy retryPolicy,
            DeadLetterQueueHandler dlqHandler) {

        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.failureClassifier = failureClassifier;
        this.retryPolicy = retryPolicy;
        this.dlqHandler = dlqHandler;
    }

    @KafkaListener(
            topics = ORDERS_TOPIC,
            groupId = "trade-executor"
    )
    public void consumeOrderPlaced(
            @Payload String message,
            Acknowledgment ack,
            @Header(
                    name = RetryPolicy.RETRY_COUNT_HEADER,
                    required = false
            ) String retryCountHeader) {

        logger.info(
                "OrderPlacedConsumer received message from Kafka: topic={} retryCount={} messageLength={} firstChars={}",
                ORDERS_TOPIC,
                retryCountHeader,
                message.length(),
                message.substring(0, Math.min(100, message.length()))
        );

        int retryCount =
                retryPolicy.parseRetryCount(retryCountHeader);

        /*
         * ---------------------------------------------------------
         * DESERIALIZATION
         * ---------------------------------------------------------
         */
        EventEnvelope<OrderPlacedPayload> orderEvent;

        try {

            orderEvent = objectMapper.readValue(
                    message,
                    objectMapper
                            .getTypeFactory()
                            .constructParametricType(
                                    EventEnvelope.class,
                                    OrderPlacedPayload.class
                            )
            );

        } catch (Exception e) {

            /*
             * Malformed JSON is permanent.
             */
            FailureClassifier.FailureClassification classification =
                    failureClassifier.classify(e, message);

            logger.warn(
                    "Permanent message failure - sending to DLQ: {}",
                    classification.getReason()
            );

            dlqHandler.sendToDeadLetterQueue(
                    message,
                    classification.getReason(),
                    ORDERS_TOPIC
            );

            ack.acknowledge();

            return;
        }

        /*
         * ---------------------------------------------------------
         * EVENT TYPE VALIDATION
         * ---------------------------------------------------------
         */
        if (orderEvent == null) {

            String reason =
                    "INVALID_DATA: Event envelope is null";

            logger.warn(reason);

            dlqHandler.sendToDeadLetterQueue(
                    message,
                    reason,
                    ORDERS_TOPIC
            );

            ack.acknowledge();

            return;
        }

        if (!EXPECTED_EVENT_TYPE.equals(
                orderEvent.getEventType())) {

            String reason =
                    "UNEXPECTED_EVENT_TYPE: Expected "
                            + EXPECTED_EVENT_TYPE
                            + " but got "
                            + orderEvent.getEventType();

            logger.warn(reason);

            dlqHandler.sendToDeadLetterQueue(
                    message,
                    reason,
                    ORDERS_TOPIC
            );

            ack.acknowledge();

            return;
        }

        /*
         * ---------------------------------------------------------
         * PAYLOAD VALIDATION
         * ---------------------------------------------------------
         */
        if (orderEvent.getPayload() == null) {

            String reason =
                    "MISSING_ORDER_ID: payload is missing";

            logger.warn(reason);

            dlqHandler.sendToDeadLetterQueue(
                    message,
                    reason,
                    ORDERS_TOPIC
            );

            ack.acknowledge();

            return;
        }

        if (orderEvent.getPayload().getOrderId() == null
                || orderEvent.getPayload()
                        .getOrderId()
                        .toString()
                        .isBlank()) {

            String reason =
                    "MISSING_ORDER_ID: orderId is missing";

            logger.warn(reason);

            dlqHandler.sendToDeadLetterQueue(
                    message,
                    reason,
                    ORDERS_TOPIC
            );

            ack.acknowledge();

            return;
        }

        /*
         * ---------------------------------------------------------
         * PROCESS ORDER
         * ---------------------------------------------------------
         */
        logger.debug(
                "Processing order: orderId={}, retryCount={}",
                orderEvent.getPayload().getOrderId(),
                retryCount
        );

        while (true) {

            try {

                executionService.executeOrder(orderEvent);

                /*
                 * Processing succeeded.
                 */
                ack.acknowledge();

                logger.info(
                        "Order processed successfully: orderId={}",
                        orderEvent.getPayload().getOrderId()
                );

                return;

            } catch (Exception e) {

                logger.error(
                        "Error processing ORDER_PLACED "
                                + "(retryCount={}): {}",
                        retryCount,
                        e.getMessage(),
                        e
                );

                FailureClassifier.FailureClassification classification =
                        failureClassifier.classify(e, message);

                /*
                 * -------------------------------------------------
                 * PERMANENT FAILURE
                 * -------------------------------------------------
                 */
                if (!classification.isRecoverable()) {

                    logger.warn(
                            "Unrecoverable error - sending "
                                    + "to DLQ: {}",
                            classification.getReason()
                    );

                    dlqHandler.sendToDeadLetterQueue(
                            message,
                            classification.getReason(),
                            ORDERS_TOPIC
                    );

                    ack.acknowledge();

                    return;
                }

                /*
                 * -------------------------------------------------
                 * RETRY AVAILABLE
                 * -------------------------------------------------
                 */
                if (retryPolicy.shouldRetry(retryCount)) {

                    long backoffMs =
                            retryPolicy.getBackoffMs(retryCount);

                    logger.warn(
                            "Recoverable error - retry {} "
                                    + "after {}ms: {}",
                            retryCount + 1,
                            backoffMs,
                            classification.getReason()
                    );

                    try {

                        Thread.sleep(backoffMs);

                    } catch (InterruptedException interruptedException) {

                        Thread.currentThread().interrupt();

                        String reason =
                                "RETRY_INTERRUPTED: "
                                        + interruptedException
                                        .getMessage();

                        logger.error(reason);

                        dlqHandler.sendToDeadLetterQueue(
                                message,
                                reason,
                                ORDERS_TOPIC
                        );

                        ack.acknowledge();

                        return;
                    }

                    retryCount++;

                    continue;
                }

                /*
                 * -------------------------------------------------
                 * RETRY BUDGET EXHAUSTED
                 * -------------------------------------------------
                 */
                String dlqReason =
                        "RETRY_BUDGET_EXHAUSTED: "
                                + classification.getReason();

                logger.warn(
                        "Retry budget exhausted - "
                                + "sending to DLQ: {}",
                        dlqReason
                );

                dlqHandler.sendToDeadLetterQueue(
                        message,
                        dlqReason,
                        ORDERS_TOPIC
                );

                ack.acknowledge();

                return;
            }
        }
    }
}