package com.capstone.service;

import com.capstone.exception.MarketDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory quote cache populated from market-data Kafka topic.
 * Used by dashboard valuation endpoints to avoid direct market API calls.
 */
@Service
public class KafkaMarketQuoteService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMarketQuoteService.class);

    private final Map<String, QuoteSnapshot> quotesBySymbol = new ConcurrentHashMap<>();

    public void updateQuote(String symbol, BigDecimal price, BigDecimal previousClose, String currency) {
        if (symbol == null || symbol.isBlank() || price == null) {
            return;
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedCurrency = (currency == null || currency.isBlank()) ? "USD" : currency;
        quotesBySymbol.put(
                normalizedSymbol,
            new QuoteSnapshot(normalizedSymbol, price, previousClose, normalizedCurrency, "kafka:market-data", Instant.now())
        );

        logger.debug(
            "Kafka quote cache updated: source=kafka:market-data symbol={} price={} previousClose={} currency={}",
            normalizedSymbol,
            price,
            previousClose,
            normalizedCurrency
        );
    }

    public QuoteSnapshot getQuote(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new MarketDataUnavailableException("Market quote symbol is missing");
        }

        QuoteSnapshot quote = quotesBySymbol.get(symbol.trim().toUpperCase());
        if (quote == null) {
            throw new MarketDataUnavailableException(
                    "Market quote is not yet available from market-data topic for symbol %s".formatted(symbol)
            );
        }

        return quote;
    }

    public record QuoteSnapshot(
            String symbol,
            BigDecimal price,
            BigDecimal previousClose,
            String currency,
            String source,
            Instant receivedAt
    ) {
    }
}