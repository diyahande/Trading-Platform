package com.capstone.kafka;

import com.capstone.entity.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KafkaOrderPlacedEventPublisher implements OrderPlacedEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.orders:orders}")
    private String ordersTopic;

    @Override
    public void publishOrderPlaced(Order order, String symbol) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "ORDER_PLACED");
        event.put("eventTime", Instant.now().toString());
        event.put("source", "trade-api");
        event.put("schemaVersion", 1);
        event.put("payload", payloadFor(order, symbol));

        String eventJson = toJson(event);
        String key = String.valueOf(order.getAccountId());
        kafkaTemplate.send(ordersTopic, key, eventJson);
    }

    private static Map<String, Object> payloadFor(Order order, String symbol) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId().toString());
        payload.put("accountId", order.getAccountId());
        payload.put("symbol", symbol);
        payload.put("side", order.getSide().name());
        payload.put("quantity", order.getQuantity().intValue());
        payload.put("price", scaleToTwo(order.getPrice()));
        payload.put("idempotencyKey", order.getIdempotencyKey());
        payload.put("createdOn", order.getCreatedAt().toString());
        return payload;
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise ORDER_PLACED event", ex);
        }
    }

    private static BigDecimal scaleToTwo(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}