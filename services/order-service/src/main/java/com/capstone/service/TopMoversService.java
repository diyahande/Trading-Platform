package com.capstone.service;

import com.capstone.dto.TopMoverResponse;
import lombok.Data;
import org.springframework.stereotype.Service;

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
