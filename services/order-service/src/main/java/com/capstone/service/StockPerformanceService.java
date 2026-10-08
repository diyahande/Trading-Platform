package com.capstone.service;

import com.capstone.dto.StockPerformanceResponse;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stock Performance Service
 * Tracks real-time performance of monitored stock symbols
 * Data is consumed from market-data Kafka topic
 * Provides visualization data for dashboard
 */
@Service
public class StockPerformanceService {

    private static final Logger logger = LoggerFactory.getLogger(StockPerformanceService.class);

    // Symbols to monitor (from market-data topic)
    private static final List<String> MONITORED_SYMBOLS = Arrays.asList(
            "AAPL", "AMZN", "CVX", "GOOGL", "IVV",
            "JNJ", "JPM", "KO", "META", "MSFT",
            "NFLX", "NVDA", "QQQ", "SPY", "TSLA",
            "V", "WMT", "XOM"
    );

    @Getter
    private static class StockQuote {
        String symbol;
        BigDecimal price;
        BigDecimal previousClose;
        BigDecimal change;
        BigDecimal changePercent;
        long lastUpdated;

        StockQuote(String symbol, BigDecimal price, BigDecimal previousClose) {
            this.symbol = symbol;
            this.price = price;
            this.previousClose = previousClose != null ? previousClose : price;
            this.change = price.subtract(this.previousClose);
            this.changePercent = this.previousClose.signum() == 0 
                ? BigDecimal.ZERO 
                : change.multiply(BigDecimal.valueOf(100))
                    .divide(this.previousClose, 2, RoundingMode.HALF_UP);
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    // Cache of stock quotes keyed by symbol
    private final Map<String, StockQuote> stockQuotes = new ConcurrentHashMap<>();

    /**
     * Update stock quote from market data
     * Called by Kafka listener on each QUOTE event
     */
    public void updateStockQuote(String symbol, BigDecimal price, BigDecimal previousClose) {
        if (MONITORED_SYMBOLS.contains(symbol)) {
            stockQuotes.put(symbol, new StockQuote(symbol, price, previousClose));
            logger.debug("Updated stock quote: symbol={} price={} changePercent={}",
                    symbol, price, stockQuotes.get(symbol).getChangePercent());
        }
    }

    /**
     * Get all stock performance data for visualization
     * Sorted by changePercent (gainers first)
     */
    public List<StockPerformanceResponse> getAllStockPerformance() {
        return stockQuotes.values().stream()
                .map(quote -> new StockPerformanceResponse(
                        quote.symbol,
                        quote.price,
                        quote.change,
                        quote.changePercent,
                        quote.lastUpdated
                ))
                .sorted(Comparator.comparing(StockPerformanceResponse::getChangePercent).reversed())
                .toList();
    }

    /**
     * Get performance data grouped by sector or performance tier
     * For heatmap visualization
     */
    public Map<String, Object> getPerformanceVisualization() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        List<StockPerformanceResponse> allStocks = getAllStockPerformance();

        if (allStocks.isEmpty()) {
            result.put("stocks", allStocks);
            result.put("statistics", Map.of(
                    "total", 0,
                    "gainers", 0,
                    "losers", 0,
                    "averageChangePercent", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            ));
            return result;
        }
        
        // Calculate statistics
        BigDecimal avgChangePercent = allStocks.stream()
                .map(StockPerformanceResponse::getChangePercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(allStocks.size()), 2, RoundingMode.HALF_UP);
        
        int gainers = (int) allStocks.stream()
                .filter(s -> s.getChangePercent().signum() > 0)
                .count();
        
        int losers = (int) allStocks.stream()
                .filter(s -> s.getChangePercent().signum() < 0)
                .count();

        result.put("stocks", allStocks);
        result.put("statistics", Map.of(
                "total", allStocks.size(),
                "gainers", gainers,
                "losers", losers,
                "averageChangePercent", avgChangePercent
        ));
        
        return result;
    }

    /**
     * Check if a symbol is monitored
     */
    public boolean isMonitored(String symbol) {
        return MONITORED_SYMBOLS.contains(symbol);
    }
}
