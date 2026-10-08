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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;



@Component
// @EnableScheduling
public class MarketDataPoller {

    private static final Logger logger =
            LoggerFactory.getLogger(MarketDataPoller.class);

    private static final int MAX_SYMBOLS_PER_BATCH = 25;

   
    private static final long MIN_INTERVAL_SECONDS = 60L;

 
    private static final long CACHE_REFRESH_INTERVAL_MS = 15 * 60 * 1000; // 15 minutes

   
    private static final Path CACHE_FILE_PATH = Paths.get(".cache/symbols-cache.txt");

    private final FauxnanceQuoteClient fauxnanceClient;
    private final MarketDataKafkaPublisher kafkaPublisher;
    private final SymbolMapper symbolMapper;
    private final long pollIntervalSeconds;
    private final AtomicLong lastPollTime = new AtomicLong(0);

    private List<String> cachedSymbols = new ArrayList<>();
    private final AtomicLong lastCacheRefreshTime = new AtomicLong(0);

    public MarketDataPoller(
            FauxnanceQuoteClient fauxnanceClient,
            MarketDataKafkaPublisher kafkaPublisher,
            SymbolMapper symbolMapper,
            @Value("${POLL_INTERVAL_SECONDS:300}") long pollIntervalSeconds) {

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
        
        // Load cache from file if it exists
        loadCacheFromFile();
    }


    @Scheduled(fixedDelayString = "${POLL_INTERVAL_SECONDS:300}000")
    public void pollMarketData() {
        try {
            long currentTime = System.currentTimeMillis();
            lastPollTime.set(currentTime);

            logger.info("Starting market data poll cycle");

            // Fetch symbols from cache or refresh if stale
            List<String> symbols = getOrRefreshSymbolsCache(currentTime);

            if (symbols == null || symbols.isEmpty()) {
                logger.info("No symbols to poll");
                return;
            }

            logger.info("Fetched {} unique symbols for polling (from cache)", symbols.size());

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

        }  catch (Exception e) {
            logger.error("Market data poller failed: {}", e.getMessage(), e);
        }
    }

 
    private synchronized List<String> getOrRefreshSymbolsCache(long currentTime) {
        long lastRefreshTime = lastCacheRefreshTime.get();
        long timeSinceLastRefresh = currentTime - lastRefreshTime;
        
        // Refresh cache if first time or stale (older than 15 minutes)
        if (lastRefreshTime == 0 || timeSinceLastRefresh >= CACHE_REFRESH_INTERVAL_MS) {
            logger.info(
                    "Refreshing symbol cache (last refresh: {} ms ago)",
                    timeSinceLastRefresh
            );
            
            try {
                List<String> fetchedSymbols = symbolMapper.fetchWatchedAndHeldSymbols();
                
                if (fetchedSymbols != null && !fetchedSymbols.isEmpty()) {
                    cachedSymbols = new ArrayList<>(fetchedSymbols);
                    lastCacheRefreshTime.set(currentTime);
                    logger.info(
                            "Symbol cache refreshed: {} symbols from database",
                            cachedSymbols.size()
                    );
                    // Persist cache to file
                    saveCacheToFile(cachedSymbols);
                } else {
                    logger.warn("Symbol cache refresh returned empty list from database");
                    lastCacheRefreshTime.set(currentTime);
                }
            } catch (Exception e) {
                logger.error("Failed to refresh symbol cache from database: {}", e.getMessage(), e);
                // If we have no cache at all, log warning
                if (cachedSymbols.isEmpty()) {
                    logger.warn("No cached symbols available and refresh failed. Market data polling may not work");
                } else {
                    logger.info("Using stale cache ({} symbols) due to database error", cachedSymbols.size());
                }
            }
        } else {
            logger.debug(
                    "Using cached symbols ({} ms since refresh, next refresh in {} ms). Count: {}",
                    timeSinceLastRefresh,
                    CACHE_REFRESH_INTERVAL_MS - timeSinceLastRefresh,
                    cachedSymbols.size()
            );
        }
        
        return new ArrayList<>(cachedSymbols);
    }

 
    private void loadCacheFromFile() {
        try {
            if (!Files.exists(CACHE_FILE_PATH)) {
                logger.debug("Cache file does not exist: {}", CACHE_FILE_PATH.toAbsolutePath());
                return;
            }
            
            List<String> symbols = Files.readAllLines(CACHE_FILE_PATH);
            if (symbols != null && !symbols.isEmpty()) {
                cachedSymbols = new ArrayList<>(symbols);
              
                logger.info(
                        "Loaded {} symbols from cache file (will refresh on first poll): {}",
                        symbols.size(),
                        symbols
                );
            }
        } catch (IOException e) {
            logger.debug("Could not load cache from file (non-fatal): {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("Unexpected error loading cache (non-fatal): {}", e.getMessage());
        }
    }

 
    private void saveCacheToFile(List<String> symbols) {
        try {
            // Create .cache directory if it doesn't exist
            Path cacheDir = CACHE_FILE_PATH.getParent();
            if (cacheDir != null && !Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
            
            // Write symbols to file (one per line)
            if (symbols != null && !symbols.isEmpty()) {
                Files.write(CACHE_FILE_PATH, symbols);
                logger.debug("Cache saved to file: {} ({} symbols)", CACHE_FILE_PATH.toAbsolutePath(), symbols.size());
            }
        } catch (IOException e) {
            logger.debug("Could not save cache to file (non-fatal): {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("Unexpected error saving cache (non-fatal): {}", e.getMessage());
        }
    }


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
    public long getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

 
    public long getMinIntervalFloor() {
        return MIN_INTERVAL_SECONDS;
    }
}
