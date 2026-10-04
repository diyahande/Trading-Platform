package com.capstone.kafka;

import com.capstone.service.StockPerformanceService;
import com.capstone.service.TopMoversService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Kafka Listener for market-data topic
 * Consumes real-time market quotes and updates:
 * - TopMoversService for stock gainers/losers
 * - StockPerformanceService for market performance visualization
 */
@Component
@RequiredArgsConstructor
public class MarketDataEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataEventListener.class);
    private final TopMoversService topMoversService;
    private final StockPerformanceService stockPerformanceService;
    private final ObjectMapper objectMapper;

    /**
     * Listen for market data quotes on the market-data topic
     * Topics: market-data (keyed by symbol, 6 partitions)
     * Group: watchlist-service (per kafka-topics.md spec)
     */
    @KafkaListener(
            topics = "market-data",
            groupId = "watchlist-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMarketDataQuote(String message) {
        try {
            // Parse the event envelope
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            
            if (event == null || !QuoteEventPayload.EVENT_TYPE.equals(event.getEventType())) {
                return;
            }

            // Extract quote payload
            QuoteEventPayload payload = objectMapper.convertValue(
                    event.getPayload(),
                    QuoteEventPayload.class
            );

            if (payload == null || payload.getSymbol() == null || payload.getPrice() == null) {
                return;
            }

            // Update services
            topMoversService.updateSymbolQuote(
                    payload.getSymbol(),
                    payload.getPrice(),
                    payload.getChange(),
                    payload.getChangePercent(),
                    payload.getPreviousClose()
            );

            // Also update stock performance service for visualization
            if (stockPerformanceService.isMonitored(payload.getSymbol())) {
                stockPerformanceService.updateStockQuote(
                        payload.getSymbol(),
                        payload.getPrice(),
                        payload.getPreviousClose()
                );
            }

            logger.debug(
                    "Market data consumed: symbol={} price={} changePercent={}",
                    payload.getSymbol(),
                    payload.getPrice(),
                    payload.getChangePercent()
            );

        } catch (Exception e) {
            logger.error("Failed to process market data message: {}", e.getMessage(), e);
            // Don't rethrow - allow Kafka to continue processing
        }
    }

    /**
     * Event envelope from Kafka topic (per kafka-topics.md spec)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventEnvelope {
        @JsonProperty("eventId")
        private String eventId;

        @JsonProperty("eventType")
        private String eventType;

        @JsonProperty("eventTime")
        private String eventTime;

        @JsonProperty("source")
        private String source;

        @JsonProperty("schemaVersion")
        private int schemaVersion;

        @JsonProperty("payload")
        private Object payload;

        public String getEventType() {
            return eventType;
        }

        public Object getPayload() {
            return payload;
        }
    }

    /**
     * Market data quote payload (per kafka-topics.md spec)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteEventPayload {
        public static final String EVENT_TYPE = "QUOTE";

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("price")
        private BigDecimal price;

        @JsonProperty("bid")
        private BigDecimal bid;

        @JsonProperty("ask")
        private BigDecimal ask;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("change")
        private BigDecimal change;

        @JsonProperty("changePercent")
        private BigDecimal changePercent;

        @JsonProperty("previousClose")
        private BigDecimal previousClose;

        @JsonProperty("marketState")
        private String marketState;

        @JsonProperty("stale")
        private boolean stale;

        @JsonProperty("quoteAsOf")
        private String quoteAsOf;

        public String getSymbol() {
            return symbol;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public BigDecimal getChange() {
            return change;
        }

        public BigDecimal getChangePercent() {
            return changePercent;
        }

        public BigDecimal getPreviousClose() {
            return previousClose;
        }
    }
}
