package com.capstone.executor.consumer;

import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.OrderPlacedPayload;
import com.capstone.executor.service.OrderExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// Consumes ORDER_PLACED from orders topic; group "trade-executor" for work queue semantics
@Component
public class OrderPlacedConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrderPlacedConsumer.class);
    private final OrderExecutionService executionService;
    private final ObjectMapper objectMapper;
    
    public OrderPlacedConsumer(OrderExecutionService executionService, ObjectMapper objectMapper) {
        this.executionService = executionService;
        this.objectMapper = objectMapper;
    }
    
    // Listen for ORDER_PLACED on orders topic
    // Manual commit: offset committed only after successful execution and event publish
    @KafkaListener(topics = "orders", groupId = "trade-executor")
    public void consumeOrderPlaced(@Payload String message, Acknowledgment ack) {
        try {
            // Deserialize to strongly-typed event
            EventEnvelope<OrderPlacedPayload> orderEvent = objectMapper.readValue(message,
                objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderPlacedPayload.class));
            
            if (!"ORDER_PLACED".equals(orderEvent.getEventType())) {
                logger.warn("Unexpected event type on orders topic: {}", orderEvent.getEventType());
                ack.acknowledge();
                return;
            }
             System.out.println("Received order: " + orderEvent.getPayload().getOrderId());
            // Execute order: load-check-fetch-decide-settle-publish
            executionService.executeOrder(orderEvent);
            
            // Acknowledge only after successful execution and event publish
            ack.acknowledge();
            
        } catch (Exception e) {
            logger.error("Error processing ORDER_PLACED: {}", e.getMessage(), e);
            // Do NOT acknowledge on error - message will be retried or sent to DLT
        }
    }
}
