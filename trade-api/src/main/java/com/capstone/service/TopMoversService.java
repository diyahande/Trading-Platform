package com.capstone.service;

import com.capstone.dto.TopMoverResponse;
import lombok.Data;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Top Movers Service
 * Tracks real-time gainers and losers based on market data from Kafka
 * Maintains top 5 gainers and top 5 losers at all times
 */
@Service
public class TopMoversService {

    private final InstrumentService instrumentService;

    // Same 18 symbols monitored from market-data Kafka topic
    private static final List<String> MONITORED_SYMBOLS = Arrays.asList(
            "AAPL", "AMZN", "CVX", "GOOGL", "IVV",
            "JNJ", "JPM", "KO", "META", "MSFT",
            "NFLX", "NVDA", "QQQ", "SPY", "TSLA",
            "V", "WMT", "XOM"
    );

    // In-memory cache of all symbol quotes
    private final Map<String, SymbolQuote> symbolQuotes = new ConcurrentHashMap<>();

    // Top gainers and losers (updated on each market data event)
    private volatile List<TopMoverResponse> topGainers = Collections.emptyList();
    private volatile List<TopMoverResponse> topLosers = Collections.emptyList();

    private static final int TOP_N = 5;

    public TopMoversService(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    /**
     * Initialize service with sample data for the 18 monitored symbols
     * Data shows realistic gains and losses for visualization
     * Real Kafka market data will update these values dynamically
     */
    @PostConstruct
    public void init() {
        // Sample data for all 18 monitored symbols with realistic prices and changes
        Map<String, BigDecimal[]> sampleData = new LinkedHashMap<>();
        sampleData.put("AAPL", new BigDecimal[]{BigDecimal.valueOf(230.5), BigDecimal.valueOf(228.0), BigDecimal.valueOf(2.5)});
        sampleData.put("AMZN", new BigDecimal[]{BigDecimal.valueOf(185.2), BigDecimal.valueOf(182.0), BigDecimal.valueOf(3.2)});
        sampleData.put("CVX", new BigDecimal[]{BigDecimal.valueOf(165.8), BigDecimal.valueOf(168.0), BigDecimal.valueOf(-2.2)});
        sampleData.put("GOOGL", new BigDecimal[]{BigDecimal.valueOf(142.3), BigDecimal.valueOf(139.5), BigDecimal.valueOf(2.8)});
        sampleData.put("IVV", new BigDecimal[]{BigDecimal.valueOf(578.4), BigDecimal.valueOf(575.0), BigDecimal.valueOf(3.4)});
        sampleData.put("JNJ", new BigDecimal[]{BigDecimal.valueOf(158.9), BigDecimal.valueOf(156.0), BigDecimal.valueOf(1.9)});
        sampleData.put("JPM", new BigDecimal[]{BigDecimal.valueOf(198.2), BigDecimal.valueOf(195.5), BigDecimal.valueOf(2.7)});
        sampleData.put("KO", new BigDecimal[]{BigDecimal.valueOf(62.4), BigDecimal.valueOf(63.0), BigDecimal.valueOf(-0.6)});
        sampleData.put("META", new BigDecimal[]{BigDecimal.valueOf(542.1), BigDecimal.valueOf(535.0), BigDecimal.valueOf(7.1)});
        sampleData.put("MSFT", new BigDecimal[]{BigDecimal.valueOf(425.8), BigDecimal.valueOf(420.0), BigDecimal.valueOf(5.8)});
        sampleData.put("NFLX", new BigDecimal[]{BigDecimal.valueOf(285.3), BigDecimal.valueOf(280.0), BigDecimal.valueOf(5.3)});
        sampleData.put("NVDA", new BigDecimal[]{BigDecimal.valueOf(138.9), BigDecimal.valueOf(135.0), BigDecimal.valueOf(3.9)});
        sampleData.put("QQQ", new BigDecimal[]{BigDecimal.valueOf(412.7), BigDecimal.valueOf(408.0), BigDecimal.valueOf(4.7)});
        sampleData.put("SPY", new BigDecimal[]{BigDecimal.valueOf(589.2), BigDecimal.valueOf(585.0), BigDecimal.valueOf(4.2)});
        sampleData.put("TSLA", new BigDecimal[]{BigDecimal.valueOf(298.5), BigDecimal.valueOf(290.0), BigDecimal.valueOf(8.5)});
        sampleData.put("V", new BigDecimal[]{BigDecimal.valueOf(268.3), BigDecimal.valueOf(265.0), BigDecimal.valueOf(3.3)});
        sampleData.put("WMT", new BigDecimal[]{BigDecimal.valueOf(98.7), BigDecimal.valueOf(97.0), BigDecimal.valueOf(1.7)});
        sampleData.put("XOM", new BigDecimal[]{BigDecimal.valueOf(112.4), BigDecimal.valueOf(110.0), BigDecimal.valueOf(2.4)});

        // Populate cache with calculated change and changePercent
        sampleData.forEach((symbol, values) -> {
            BigDecimal price = values[0];
            BigDecimal previousClose = values[1];
            BigDecimal change = price.subtract(previousClose);
            BigDecimal changePercent = previousClose.signum() == 0 
                ? BigDecimal.ZERO 
                : change.multiply(BigDecimal.valueOf(100))
                    .divide(previousClose, 2, RoundingMode.HALF_UP);

            SymbolQuote quote = new SymbolQuote(symbol, price, change, changePercent, previousClose);
            symbolQuotes.put(symbol, quote);
        });

        // Calculate initial top movers from sample data
        recalculateTopMovers();
    }

    /**
     * Update symbol quote from market data
     * This is called by the Kafka listener when new market data arrives
     */
    public void updateSymbolQuote(String symbol, BigDecimal price, BigDecimal change, BigDecimal changePercent, BigDecimal previousClose) {
        if (symbol == null || price == null) {
            return;
        }

        SymbolQuote quote = new SymbolQuote(symbol, price, change, changePercent, previousClose);
        symbolQuotes.put(symbol, quote);

        // Recalculate top movers
        recalculateTopMovers();
    }

    /**
     * Get top 5 gainers
     */
    public List<TopMoverResponse> getTopGainers() {
        return new ArrayList<>(topGainers);
    }

    /**
     * Get top 5 losers
     */
    public List<TopMoverResponse> getTopLosers() {
        return new ArrayList<>(topLosers);
    }

    /**
     * Recalculate top movers based on current quotes
     * Called after each market data update
     */
    private synchronized void recalculateTopMovers() {
        if (symbolQuotes.isEmpty()) {
            topGainers = Collections.emptyList();
            topLosers = Collections.emptyList();
            return;
        }

        // Filter out nulls and sort by changePercent
        List<SymbolQuote> sortedByChange = symbolQuotes.values().stream()
                .filter(q -> q.changePercent != null && q.price != null)
                .collect(Collectors.toList());

        // Get top gainers (highest changePercent)
        topGainers = sortedByChange.stream()
                .sorted((a, b) -> b.changePercent.compareTo(a.changePercent))
                .limit(TOP_N)
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        // Get top losers (lowest changePercent)
        topLosers = sortedByChange.stream()
                .sorted(Comparator.comparing((SymbolQuote q) -> q.changePercent))
                .limit(TOP_N)
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert internal SymbolQuote to TopMoverResponse
     * Enriches with instrument data (display name, asset class)
     */
    private TopMoverResponse convertToResponse(SymbolQuote quote) {
        try {
            // Try to get instrument details from database
            var instrumentOpt = instrumentService.getInstrumentBySymbol(quote.symbol);
            if (instrumentOpt.isPresent()) {
                var instrument = instrumentOpt.get();
                return new TopMoverResponse(
                        quote.symbol,
                        instrument.getDisplayName(),
                        instrument.getAssetClass().toString(),
                        quote.price,
                        quote.change,
                        quote.changePercent
                );
            }
        } catch (Exception e) {
            // Fall through to fallback
        }
        
        // Fallback: use symbol as display name
        return new TopMoverResponse(
                quote.symbol,
                quote.symbol,
                "EQUITY",
                quote.price,
                quote.change,
                quote.changePercent
        );
    }

    /**
     * Clear all cached quotes (for testing or reset)
     */
    public void clearCache() {
        symbolQuotes.clear();
        topGainers = Collections.emptyList();
        topLosers = Collections.emptyList();
    }

    /**
     * Get current quote for a symbol
     */
    public SymbolQuote getSymbolQuote(String symbol) {
        return symbolQuotes.get(symbol);
    }

    /**
     * Internal class to track quote data
     */
    @Data
    public static class SymbolQuote {
        public String symbol;
        public BigDecimal price;
        public BigDecimal change;
        public BigDecimal changePercent;
        public BigDecimal previousClose;

        public SymbolQuote(String symbol, BigDecimal price, BigDecimal change,
                          BigDecimal changePercent, BigDecimal previousClose) {
            this.symbol = symbol;
            this.price = price;
            this.change = change;
            this.changePercent = changePercent;
            this.previousClose = previousClose;
        }
    }
}
