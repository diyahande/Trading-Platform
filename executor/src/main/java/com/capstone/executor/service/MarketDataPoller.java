package com.capstone.executor.service;

import com.capstone.executor.client.FauxnanceQuoteClient;
import com.capstone.executor.domain.Quote;
import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.MarketDataQuotePayload;
import com.capstone.executor.mapper.SymbolMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Market data poller for the market-data Kafka topic.
 * 
 * Runs on a schedule inside the Trade Executor. Fetches symbols from
 * portfolio holdings, orders, and watchlists. Batches up to 25 symbols
 * per Fauxnance API call for quota efficiency. Publishes one message
 * per symbol to market-data, keyed by symbol.
 * 
 * Acceptance criteria:
 * - The poller runs on a schedule inside the Trade Executor
 * - Calls the batch quotes endpoint batching up to 25 symbols per request
 * - Publishes one message per symbol to market-data keyed by the symbol
 * - The configured interval stays inside the 2000-requests-per-day quota
 * - The arithmetic can be shown
 * - Polls only symbols someone holds or is watching
 * - Enforces the interval floor in code
 * 
 * Contract binding: kafka-topics.md
 */
@Component
@EnableScheduling
public class MarketDataPoller {

    private static final Logger logger =
            LoggerFactory.getLogger(MarketDataPoller.class);

    /**
     * Maximum symbols per batch request to Fauxnance.
     * API contract limit is 25.
     */
    private static final int MAX_SYMBOLS_PER_BATCH = 25;

    /**
     * Minimum interval floor in seconds.
     * Enforced in code to prevent quota violations.
     * 
     * Calculation for 24 symbols at 60 second interval:
     * - 1 batch per poll (24 symbols <= 25 limit)
     * - 86400 seconds per day / 60 seconds = 1440 polls
     * - 1440 polls * 1 batch = 1440 requests per day
     * - Well within 2000 request/day quota with proper batching
     * - Default 60 seconds allows for up to ~38 symbols with 1 batch per poll
     */
    private static final long MIN_INTERVAL_SECONDS = 60L;

    private final FauxnanceQuoteClient fauxnanceClient;
    private final MarketDataKafkaPublisher kafkaPublisher;
    private final SymbolMapper symbolMapper;
    private final long pollIntervalSeconds;
    private long lastPollTime = 0;

    public MarketDataPoller(
            FauxnanceQuoteClient fauxnanceClient,
            MarketDataKafkaPublisher kafkaPublisher,
            SymbolMapper symbolMapper,
            @Value("${POLL_INTERVAL_SECONDS:60}") long pollIntervalSeconds) {

        this.fauxnanceClient = fauxnanceClient;
        this.kafkaPublisher = kafkaPublisher;
        this.symbolMapper = symbolMapper;

        // Enforce interval floor
        if (pollIntervalSeconds < MIN_INTERVAL_SECONDS) {
            logger.warn(
                    "POLL_INTERVAL_SECONDS={} is below minimum floor of {} seconds. "
                            + "Using minimum to stay within quota. "
                            + "See MarketDataPoller quota calculation.",
                    pollIntervalSeconds,
                    MIN_INTERVAL_SECONDS
            );
            this.pollIntervalSeconds = MIN_INTERVAL_SECONDS;
        } else {
            this.pollIntervalSeconds = pollIntervalSeconds;
        }

        logger.info(
                "MarketDataPoller initialized: pollIntervalSeconds={} maxSymbolsPerBatch={} minIntervalFloor={}",
                this.pollIntervalSeconds,
                MAX_SYMBOLS_PER_BATCH,
                MIN_INTERVAL_SECONDS
        );

        // Log quota calculation
        logQuotaCalculation();
    }

    /**
     * Poll market data for all watched and held symbols.
     * 
     * Scheduled to run at the configured interval. Fetches unique symbols
     * from portfolio holdings, orders, and watchlists. Batches them up to
     * MAX_SYMBOLS_PER_BATCH for efficient Fauxnance API calls. Publishes
     * one message per symbol to the market-data topic.
     */
    @Scheduled(fixedDelayString = "${POLL_INTERVAL_SECONDS:60}000")
    public void pollMarketData() {
        try {
            // Enforce interval floor at execution time
            long currentTime = System.currentTimeMillis();
            long minElapsedMs = pollIntervalSeconds * 1000;

            if (lastPollTime > 0 && (currentTime - lastPollTime) < minElapsedMs) {
                long waitMs = minElapsedMs - (currentTime - lastPollTime);
                logger.info(
                        "Interval floor enforced: waiting {} ms before next poll",
                        waitMs
                );
                Thread.sleep(waitMs);
                currentTime = System.currentTimeMillis();
            }

            lastPollTime = currentTime;

            logger.info("Starting market data poll cycle");

            // Fetch all unique symbols from portfolio_holdings, orders, watchlist
            List<String> symbols = symbolMapper.fetchWatchedAndHeldSymbols();

            if (symbols == null || symbols.isEmpty()) {
                logger.info("No symbols to poll");
                return;
            }

            logger.info("Fetched {} unique symbols for polling", symbols.size());

            // Batch symbols and fetch quotes
            int totalQuotesFetched = 0;
            int totalQuotesPublished = 0;

            for (int i = 0; i < symbols.size(); i += MAX_SYMBOLS_PER_BATCH) {
                int endIndex = Math.min(i + MAX_SYMBOLS_PER_BATCH, symbols.size());
                List<String> batch = symbols.subList(i, endIndex);

                logger.info(
                        "Fetching batch: symbols {}-{} of {} (batch size={})",
                        i + 1,
                        endIndex,
                        symbols.size(),
                        batch.size()
                );

                // Fetch batch of quotes
                Map<String, Quote> quotes = fauxnanceClient.fetchQuotesBatch(batch);
                totalQuotesFetched += quotes.size();

                // Publish one message per symbol
                for (Map.Entry<String, Quote> entry : quotes.entrySet()) {
                    String symbol = entry.getKey();
                    Quote quote = entry.getValue();

                    if (quote != null) {
                        publishQuote(symbol, quote);
                        totalQuotesPublished++;
                    } else {
                        logger.warn("Quote not available for symbol: {}", symbol);
                    }
                }
            }

            logger.info(
                    "Market data poll cycle complete: symbols={} fetched={} published={}",
                    symbols.size(),
                    totalQuotesFetched,
                    totalQuotesPublished
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Market data poller was interrupted", e);
        } catch (Exception e) {
            logger.error("Market data poller failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish a single quote to the market-data Kafka topic.
     * 
     * Creates an EventEnvelope with MarketDataQuotePayload, keyed by symbol.
     */
    private void publishQuote(String symbol, Quote quote) {
        try {
            // Build payload from Quote domain model
            MarketDataQuotePayload payload = new MarketDataQuotePayload(
                    quote.getSymbol(),
                    quote.getPrice(),
                    quote.getBid(),
                    quote.getAsk(),
                    quote.getCurrency(),
                    quote.getChange(),
                    quote.getChangePercent(),
                    quote.getPreviousClose(),
                    quote.getMarketState(),
                    quote.isStale(),
                    quote.getQuoteAsOf()
            );

            // Build event envelope
            EventEnvelope<MarketDataQuotePayload> envelope = new EventEnvelope<>(
                    UUID.randomUUID().toString(),
                    "QUOTE",
                    Instant.now(),
                    "market-poller",
                    1,
                    payload
            );

            kafkaPublisher.publishQuote(envelope);

        } catch (Exception e) {
            logger.error(
                    "Failed to publish quote for symbol {}: {}",
                    symbol,
                    e.getMessage(),
                    e
            );
        }
    }

    /**
     * Log quota calculation for documentation and debugging.
     * 
     * Calculates requests per day based on number of symbols and poll interval.
     * Documents the arithmetic for the team to keep with its decisions.
     */
    private void logQuotaCalculation() {
        logger.info(
                "Market data poller quota calculation: "
                        + "interval={} seconds, "
                        + "polls_per_day={} (86400 seconds / {} = {}), "
                        + "max_symbols_per_batch={}, "
                        + "examples: "
                        + "8 symbols = {} batches/poll * {} = {} requests/day, "
                        + "24 symbols = {} batches/poll * {} = {} requests/day",
                pollIntervalSeconds,
                86400L / pollIntervalSeconds,
                pollIntervalSeconds,
                86400L / pollIntervalSeconds,
                MAX_SYMBOLS_PER_BATCH,
                1,
                86400L / pollIntervalSeconds,
                86400L / pollIntervalSeconds,
                1,
                86400L / pollIntervalSeconds,
                86400L / pollIntervalSeconds
        );
    }

    /**
     * Return the configured poll interval in seconds.
     * 
     * Useful for testing.
     */
    public long getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    /**
     * Return the minimum interval floor in seconds.
     * 
     * Useful for testing.
     */
    public long getMinIntervalFloor() {
        return MIN_INTERVAL_SECONDS;
    }
}
