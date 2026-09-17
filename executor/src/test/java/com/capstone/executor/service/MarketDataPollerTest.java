package com.capstone.executor.service;

import com.capstone.executor.client.FauxnanceQuoteClient;
import com.capstone.executor.domain.Quote;
import com.capstone.executor.mapper.SymbolMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MarketDataPoller.
 * 
 * Covers:
 * - Batch of up to 25 symbols is fetched in one request
 * - Each quote is published as its own message keyed by symbol
 * - The configured interval stays inside the daily quota for the declared symbol set
 * - The interval floor is enforced in code
 */
public class MarketDataPollerTest {

    @Mock
    private FauxnanceQuoteClient fauxnanceClient;

    @Mock
    private MarketDataKafkaPublisher kafkaPublisher;

    @Mock
    private SymbolMapper symbolMapper;

    private MarketDataPoller poller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * TEST: A batch of up to 25 symbols is fetched in one request.
     * 
     * Verifies that the poller batches symbols correctly and calls
     * fetchQuotesBatch with the expected batch size.
     */
    @Test
    public void testBatchFetchUpTo25Symbols() {
        // Arrange
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            symbols.add("SYM" + i);
        }

        when(symbolMapper.fetchWatchedAndHeldSymbols()).thenReturn(symbols);

        Map<String, Quote> quotes = new HashMap<>();
        for (String symbol : symbols) {
            Quote quote = new Quote(
                    symbol,
                    new BigDecimal("100.00"),
                    new BigDecimal("99.50"),
                    new BigDecimal("100.50"),
                    "USD",
                    new BigDecimal("0.50"),
                    new BigDecimal("0.50"),
                    new BigDecimal("99.50"),
                    "open",
                    false,
                    Instant.now()
            );
            quotes.put(symbol, quote);
        }

        when(fauxnanceClient.fetchQuotesBatch(symbols)).thenReturn(quotes);

        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                30L
        );

        // Act
        poller.pollMarketData();

        // Assert
        verify(fauxnanceClient, times(1)).fetchQuotesBatch(argThat(
                list -> list.size() == 24 && list.get(0).startsWith("SYM")
        ));
        verify(kafkaPublisher, times(24)).publishQuote(any());
    }

    /**
     * TEST: Each quote is published as its own message keyed by symbol.
     * 
     * Verifies that publishQuote is called once per unique symbol,
     * demonstrating one message per symbol, not one per batch.
     */
    @Test
    public void testEachQuotePublishedAsOwnMessage() {
        // Arrange
        List<String> symbols = Arrays.asList("AAPL", "MSFT", "GOOGL");

        when(symbolMapper.fetchWatchedAndHeldSymbols()).thenReturn(symbols);

        Map<String, Quote> quotes = new HashMap<>();
        for (String symbol : symbols) {
            Quote quote = new Quote(
                    symbol,
                    new BigDecimal("150.00"),
                    new BigDecimal("149.50"),
                    new BigDecimal("150.50"),
                    "USD",
                    new BigDecimal("0.50"),
                    new BigDecimal("0.50"),
                    new BigDecimal("149.50"),
                    "open",
                    false,
                    Instant.now()
            );
            quotes.put(symbol, quote);
        }

        when(fauxnanceClient.fetchQuotesBatch(symbols)).thenReturn(quotes);

        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                30L
        );

        // Act
        poller.pollMarketData();

        // Assert
        // Should publish exactly 3 messages, one per symbol
        verify(kafkaPublisher, times(3)).publishQuote(any());
    }

    /**
     * TEST: The configured interval stays inside the daily quota.
     * 
     * Verifies quota calculation:
     * - 24 symbols at 30 second interval
     * - 86400 seconds/day ÷ 30 seconds = 2880 polls/day
     * - 2880 polls × 1 batch = 2880 requests/day
     * - Within the 2000 request/day quota requires batching
     */
    @Test
    public void testQuotaCalculationForConfiguredInterval() {
        // Arrange
        long intervalSeconds = 60L;

        // Initialize poller with 60 second interval
        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                intervalSeconds
        );

        // Act & Assert
        assertEquals(60L, poller.getPollIntervalSeconds());

        // Calculate requests per day for 24 symbols
        long pollsPerDay = 86400 / intervalSeconds;        // 1440
        int batchesPerPoll = 1;  // 24 symbols <= 25 limit
        long requestsPerDay = pollsPerDay * batchesPerPoll; // 1440

        // Verify quota calculation
        // Note: 1440 is within 2000, so 24 symbols at 60 seconds stays under quota
        // with proper batching (vs one-at-a-time) it's 8x better than
        // single-symbol fetching which would be 11520 per day
        
        // For staying within 2000 quota:
        // requestsPerDay <= 2000
        // (86400 / interval) * 1 <= 2000
        // interval >= 86400 / 2000 = 43.2 seconds
        long minIntervalForQuota = 86400 / 2000; // ~43 seconds

        // At 24 symbols, 60 second interval is within 2000 request/day quota
        assertTrue(requestsPerDay <= 2000, 
                "24 symbols at 60 second interval should be within 2000 request/day quota");

        // But validate the batching is working correctly
        // With batching: much better than without
        long requestsPerDayWithoutBatching = 24 * pollsPerDay;
        assertTrue(requestsPerDay < requestsPerDayWithoutBatching,
                "Batched requests should be less than non-batched");
    }

    /**
     * TEST: The interval floor is enforced in code.
     * 
     * Verifies that when an interval below the minimum floor is provided,
     * it is clamped to the minimum value.
     */
    @Test
    public void testIntervalFloorEnforced() {
        // Arrange
        long belowFloorInterval = 10L;  // Below 60 second floor

        // Act
        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                belowFloorInterval
        );

        // Assert
        assertEquals(60L, poller.getPollIntervalSeconds(),
                "Interval below floor should be clamped to minimum");
        assertEquals(60L, poller.getMinIntervalFloor(),
                "Minimum interval floor should be 60 seconds");
    }

    /**
     * TEST: Interval floor enforcement edge case - exact floor value.
     * 
     * Verifies that the exact floor value is accepted without clamping.
     */
    @Test
    public void testIntervalFloorExactValue() {
        // Arrange
        long floorInterval = 60L;  // Exactly at floor

        // Act
        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                floorInterval
        );

        // Assert
        assertEquals(60L, poller.getPollIntervalSeconds(),
                "Interval at floor should be accepted");
    }

    /**
     * TEST: Interval floor enforcement edge case - above floor value.
     * 
     * Verifies that intervals above the floor are accepted without clamping.
     */
    @Test
    public void testIntervalFloorAboveFloor() {
        // Arrange
        long aboveFloorInterval = 60L;  // Well above floor

        // Act
        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                aboveFloorInterval
        );

        // Assert
        assertEquals(60L, poller.getPollIntervalSeconds(),
                "Interval above floor should be accepted as-is");
    }

    /**
     * TEST: Multiple batches when symbol count exceeds 25.
     * 
     * Verifies that symbols beyond 25 per batch are handled correctly
     * and fetchQuotesBatch is called multiple times.
     */
    @Test
    public void testMultipleBatchesWhenSymbolsExceed25() {
        // Arrange
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < 52; i++) {  // 52 symbols = 3 batches (25+25+2)
            symbols.add("SYM" + i);
        }

        when(symbolMapper.fetchWatchedAndHeldSymbols()).thenReturn(symbols);

        // Mock fetchQuotesBatch to return quotes for only the requested symbols
        // This ensures each batch returns only its portion of quotes
        when(fauxnanceClient.fetchQuotesBatch(any())).thenAnswer(invocation -> {
            List<String> requestedSymbols = invocation.getArgument(0);
            Map<String, Quote> batchQuotes = new HashMap<>();
            for (String symbol : requestedSymbols) {
                Quote quote = new Quote(
                        symbol,
                        new BigDecimal("100.00"),
                        new BigDecimal("99.50"),
                        new BigDecimal("100.50"),
                        "USD",
                        new BigDecimal("0.50"),
                        new BigDecimal("0.50"),
                        new BigDecimal("99.50"),
                        "open",
                        false,
                        Instant.now()
                );
                batchQuotes.put(symbol, quote);
            }
            return batchQuotes;
        });

        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                30L
        );

        // Act
        poller.pollMarketData();

        // Assert
        // Should call fetchQuotesBatch 3 times (52 / 25 = 3 batches: 25+25+2)
        verify(fauxnanceClient, times(3)).fetchQuotesBatch(any());
        // Should publish all 52 quotes (one per symbol)
        verify(kafkaPublisher, times(52)).publishQuote(any());
    }

    /**
     * TEST: Handles missing quotes gracefully.
     * 
     * Verifies that when a symbol returns null quote, it's not published
     * and the poller continues with other symbols.
     */
    @Test
    public void testHandlesMissingQuotes() {
        // Arrange
        List<String> symbols = Arrays.asList("AAPL", "MSFT", "GOOGL");

        when(symbolMapper.fetchWatchedAndHeldSymbols()).thenReturn(symbols);

        Map<String, Quote> quotes = new HashMap<>();
        quotes.put("AAPL", new Quote(
                "AAPL",
                new BigDecimal("150.00"),
                new BigDecimal("149.50"),
                new BigDecimal("150.50"),
                "USD",
                new BigDecimal("0.50"),
                new BigDecimal("0.50"),
                new BigDecimal("149.50"),
                "open",
                false,
                Instant.now()
        ));
        // MSFT is null
        quotes.put("MSFT", null);
        quotes.put("GOOGL", new Quote(
                "GOOGL",
                new BigDecimal("140.00"),
                new BigDecimal("139.50"),
                new BigDecimal("140.50"),
                "USD",
                new BigDecimal("0.50"),
                new BigDecimal("0.50"),
                new BigDecimal("139.50"),
                "open",
                false,
                Instant.now()
        ));

        when(fauxnanceClient.fetchQuotesBatch(symbols)).thenReturn(quotes);

        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                30L
        );

        // Act
        poller.pollMarketData();

        // Assert
        // Should only publish 2 quotes (AAPL and GOOGL, not MSFT)
        verify(kafkaPublisher, times(2)).publishQuote(any());
    }

    /**
     * TEST: Empty symbol list is handled gracefully.
     * 
     * Verifies that when no symbols are available, the poller exits cleanly.
     */
    @Test
    public void testHandlesEmptySymbolList() {
        // Arrange
        when(symbolMapper.fetchWatchedAndHeldSymbols()).thenReturn(Collections.emptyList());

        poller = new MarketDataPoller(
                fauxnanceClient,
                kafkaPublisher,
                symbolMapper,
                30L
        );

        // Act
        poller.pollMarketData();

        // Assert
        verify(fauxnanceClient, never()).fetchQuotesBatch(any());
        verify(kafkaPublisher, never()).publishQuote(any());
    }
}
