package com.capstone.service;

import com.capstone.exception.MarketDataUnavailableException;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class KafkaMarketQuoteService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMarketQuoteService.class);

    private final Map<String, QuoteSnapshot> quotesBySymbol = new ConcurrentHashMap<>();
    private final MarketQuoteService marketQuoteService;

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

        String normalizedSymbol = symbol.trim().toUpperCase();
        QuoteSnapshot quote = quotesBySymbol.get(normalizedSymbol);

        if (quote == null) {
            // Fallback for exchange-suffixed watchlist symbols (e.g., INFY.NS -> INFY)
            // when market-data stream publishes base ticker keys.
            int exchangeSeparator = normalizedSymbol.indexOf('.');
            if (exchangeSeparator > 0) {
                String baseSymbol = normalizedSymbol.substring(0, exchangeSeparator);
                quote = quotesBySymbol.get(baseSymbol);

                if (quote == null) {
                    quote = fetchOnDemandAndCache(normalizedSymbol, baseSymbol);
                }
            } else {
                quote = fetchOnDemandAndCache(normalizedSymbol, normalizedSymbol);
            }
        }

        if (quote == null) {
            throw new MarketDataUnavailableException(
                    "Market quote is not yet available from market-data topic for symbol %s".formatted(symbol)
            );
        }

        return quote;
    }

    private QuoteSnapshot fetchOnDemandAndCache(String requestedSymbol, String quoteSymbol) {
        try {
            MarketQuoteService.QuoteSnapshot fetched = marketQuoteService.getQuote(quoteSymbol);
            String normalizedFetchedSymbol = fetched.symbol() == null || fetched.symbol().isBlank()
                    ? quoteSymbol
                    : fetched.symbol().trim().toUpperCase();
            String normalizedCurrency = (fetched.currency() == null || fetched.currency().isBlank())
                    ? "USD"
                    : fetched.currency().trim().toUpperCase();

            QuoteSnapshot snapshot = new QuoteSnapshot(
                    normalizedFetchedSymbol,
                    fetched.price(),
                    fetched.previousClose(),
                    normalizedCurrency,
                    "fauxnance:on-demand",
                    Instant.now()
            );

            quotesBySymbol.put(normalizedFetchedSymbol, snapshot);
            quotesBySymbol.put(requestedSymbol, snapshot);
            quotesBySymbol.put(quoteSymbol, snapshot);

            logger.debug(
                    "Quote cache miss resolved via on-demand fetch: requestedSymbol={} quoteSymbol={} resolvedSymbol={} price={}",
                    requestedSymbol,
                    quoteSymbol,
                    normalizedFetchedSymbol,
                    fetched.price()
            );

            return snapshot;
        } catch (MarketDataUnavailableException ex) {
            return null;
        }
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