package com.capstone.service;

import com.capstone.dto.StockPerformanceResponse;
import jakarta.annotation.PostConstruct;
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
     * Initialize with sample data for demonstration
     */
    @PostConstruct
    public void initializeWithSampleData() {
        // Sample price data for visualization
        Map<String, BigDecimal[]> sampleData = new LinkedHashMap<>();
        sampleData.put("AAPL", new BigDecimal[]{BigDecimal.valueOf(230.5), BigDecimal.valueOf(228.0)});
        sampleData.put("AMZN", new BigDecimal[]{BigDecimal.valueOf(185.2), BigDecimal.valueOf(182.0)});
        sampleData.put("CVX", new BigDecimal[]{BigDecimal.valueOf(165.8), BigDecimal.valueOf(168.0)});
        sampleData.put("GOOGL", new BigDecimal[]{BigDecimal.valueOf(142.3), BigDecimal.valueOf(139.5)});
        sampleData.put("IVV", new BigDecimal[]{BigDecimal.valueOf(578.4), BigDecimal.valueOf(575.0)});
        sampleData.put("JNJ", new BigDecimal[]{BigDecimal.valueOf(158.9), BigDecimal.valueOf(156.0)});
        sampleData.put("JPM", new BigDecimal[]{BigDecimal.valueOf(198.2), BigDecimal.valueOf(195.5)});
        sampleData.put("KO", new BigDecimal[]{BigDecimal.valueOf(62.4), BigDecimal.valueOf(63.0)});
        sampleData.put("META", new BigDecimal[]{BigDecimal.valueOf(542.1), BigDecimal.valueOf(535.0)});
        sampleData.put("MSFT", new BigDecimal[]{BigDecimal.valueOf(425.8), BigDecimal.valueOf(420.0)});
        sampleData.put("NFLX", new BigDecimal[]{BigDecimal.valueOf(285.3), BigDecimal.valueOf(280.0)});
        sampleData.put("NVDA", new BigDecimal[]{BigDecimal.valueOf(138.9), BigDecimal.valueOf(135.0)});
        sampleData.put("QQQ", new BigDecimal[]{BigDecimal.valueOf(412.7), BigDecimal.valueOf(408.0)});
        sampleData.put("SPY", new BigDecimal[]{BigDecimal.valueOf(589.2), BigDecimal.valueOf(585.0)});
        sampleData.put("TSLA", new BigDecimal[]{BigDecimal.valueOf(298.5), BigDecimal.valueOf(290.0)});
        sampleData.put("V", new BigDecimal[]{BigDecimal.valueOf(268.3), BigDecimal.valueOf(265.0)});
        sampleData.put("WMT", new BigDecimal[]{BigDecimal.valueOf(98.7), BigDecimal.valueOf(97.0)});
        sampleData.put("XOM", new BigDecimal[]{BigDecimal.valueOf(112.4), BigDecimal.valueOf(110.0)});

        sampleData.forEach((symbol, prices) ->
            stockQuotes.put(symbol, new StockQuote(symbol, prices[0], prices[1]))
        );

        logger.info("Initialized StockPerformanceService with {} symbols", stockQuotes.size());
    }

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
