package com.capstone.executor.service;

import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.TradeEventPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Publishes trade events to trade-events topic
@Component
public class TradeEventKafkaPublisher implements TradeEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(TradeEventKafkaPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public TradeEventKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void publish(EventEnvelope<TradeEventPayload> event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            // Key by accountId for per-account ordering guarantee
            String key = event.getPayload().getAccountId().toString();
            kafkaTemplate.send("trade-events", key, eventJson);
            logger.info("Trade event published: orderId={} eventType={}", event.getPayload().getOrderId(), event.getEventType());
        } catch (Exception e) {
            logger.error("Failed to publish trade event: {}", e.getMessage(), e);
            throw new RuntimeException("Trade event publish failed", e);
        }
    }
}
