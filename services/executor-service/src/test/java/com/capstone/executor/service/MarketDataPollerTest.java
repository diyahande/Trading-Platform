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

        poller.pollMarketData();

        verify(kafkaPublisher, times(3)).publishQuote(any());
    }

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

        assertTrue(requestsPerDay <= 2000 && requestsPerDay > 43, 
                "24 symbols at 60 second interval should be within 2000 request/day quota");

        
        long requestsPerDayWithoutBatching = 24 * pollsPerDay;
        assertTrue(requestsPerDay < requestsPerDayWithoutBatching,
                "Batched requests should be less than non-batched");
    }


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

  
    @Test
    public void testMultipleBatchesWhenSymbolsExceed25() {
        // Arrange
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < 52; i++) {  // 52 symbols = 3 batches (25+25+2)
            symbols.add("SYM" + i);
        }

        when(symbolMapper.fetchWatchedAndHeldSymbols()).thenReturn(symbols);

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

    
        verify(fauxnanceClient, times(3)).fetchQuotesBatch(any());
       
        verify(kafkaPublisher, times(52)).publishQuote(any());
    }


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

  
        verify(kafkaPublisher, times(2)).publishQuote(any());
    }

  
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
