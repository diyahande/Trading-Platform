package com.capstone.kafka;

import com.capstone.entity.Order;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaOrderPlacedEventPublisherTest {

    @Test
    void publishOrderPlaced_sendsContractEnvelopeToOrdersTopic() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();

        KafkaOrderPlacedEventPublisher publisher = new KafkaOrderPlacedEventPublisher(kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "ordersTopic", "orders");

        Order order = new Order(
                UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e"),
                42L,
                "11111111-1111-4111-8111-111111111111",
                200L,
                OrderSide.BUY,
                new BigDecimal("3"),
                new BigDecimal("233.0"),
                OrderStatus.NEW,
                "idem-abc-12345",
                Instant.parse("2026-09-28T09:14:22Z")
        );

        publisher.publishOrderPlaced(order, "AAPL");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(org.mockito.Mockito.eq("orders"), keyCaptor.capture(), valueCaptor.capture());

        assertEquals("42", keyCaptor.getValue());

        JsonNode root = objectMapper.readTree(valueCaptor.getValue());
        assertEquals("ORDER_PLACED", root.get("eventType").asText());
        assertEquals("trade-api", root.get("source").asText());
        assertEquals(1, root.get("schemaVersion").asInt());
        assertTrue(root.get("eventId").asText().length() > 0);
        assertTrue(root.get("eventTime").asText().length() > 0);

        JsonNode payload = root.get("payload");
        assertEquals("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e", payload.get("orderId").asText());
        assertEquals(42L, payload.get("accountId").asLong());
        assertEquals("AAPL", payload.get("symbol").asText());
        assertEquals("BUY", payload.get("side").asText());
        assertEquals(3, payload.get("quantity").asInt());
        assertEquals("233.00", payload.get("price").decimalValue().setScale(2).toPlainString());
        assertEquals("idem-abc-12345", payload.get("idempotencyKey").asText());
        assertEquals("2026-09-28T09:14:22Z", payload.get("createdOn").asText());
    }
}