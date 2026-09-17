package com.capstone.executor.service;

import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.MarketDataQuotePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes market data quotes to the market-data Kafka topic.
 * 
 * Contract binding: kafka-topics.md
 * 
 * One message per symbol, keyed by symbol for per-symbol ordering guarantee.
 * The market-data topic ensures no consumer sees an older quote for a symbol
 * after a newer one.
 */
@Component
public class MarketDataKafkaPublisher {
    private static final Logger logger =
            LoggerFactory.getLogger(MarketDataKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public MarketDataKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a single quote to market-data topic.
     * 
     * Keyed by symbol to guarantee per-symbol ordering. One message per symbol,
     * never batched.
     */
    public void publishQuote(EventEnvelope<MarketDataQuotePayload> event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            // Key by symbol for per-symbol ordering guarantee
            String key = event.getPayload().getSymbol();
            kafkaTemplate.send("market-data", key, eventJson);
            logger.info(
                    "Market data quote published: symbol={} price={} eventType={}",
                    event.getPayload().getSymbol(),
                    event.getPayload().getPrice(),
                    event.getEventType()
            );
        } catch (Exception e) {
            logger.error(
                    "Failed to publish market data quote: {}",
                    e.getMessage(),
                    e
            );
            throw new RuntimeException("Market data quote publish failed", e);
        }
    }
}
