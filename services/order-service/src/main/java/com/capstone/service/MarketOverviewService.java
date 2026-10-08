package com.capstone.service;

import com.capstone.dto.IndexQuoteResponse;
import com.capstone.dto.MarketOverviewResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Market Overview Service
 * Manages market index data (NIFTY 50, SENSEX, NASDAQ, S&P 500)
 * Stores quotes and historical data for charting
 */
@Service
public class MarketOverviewService {

    private final KafkaMarketQuoteService kafkaMarketQuoteService;

    // Index symbol mappings
    private static final Map<String, String> INDEX_SYMBOLS = new LinkedHashMap<>();

    static {
        INDEX_SYMBOLS.put("NIFTY_50", "^NSEI");      // NIFTY 50 Index
        INDEX_SYMBOLS.put("SENSEX", "^BSESN");        // BSE Sensex Index
        INDEX_SYMBOLS.put("NASDAQ", "^IXIC");         // NASDAQ Composite
        INDEX_SYMBOLS.put("SP500", "^GSPC");          // S&P 500 Index
    }

    // In-memory storage of latest quotes for each index
    private final Map<String, IndexQuoteData> indexQuotes = new ConcurrentHashMap<>();

    // Historical data for charting (last 100 quotes per index)
    private final Map<String, LinkedList<QuoteHistory>> indexHistory;

    public MarketOverviewService(KafkaMarketQuoteService kafkaMarketQuoteService) {
        this.kafkaMarketQuoteService = kafkaMarketQuoteService;
        // Initialize historical data structures
        this.indexHistory = new ConcurrentHashMap<>();
        for (String indexKey : INDEX_SYMBOLS.keySet()) {
            this.indexHistory.put(indexKey, new LinkedList<>());
        }
    }

    /**
     * Get current market overview for all indices
     */
    public MarketOverviewResponse getMarketOverview() {
        Map<String, IndexQuoteResponse> indices = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : INDEX_SYMBOLS.entrySet()) {
            String indexKey = entry.getKey();
            String symbol = entry.getValue();

            try {
                var quote = kafkaMarketQuoteService.getQuote(symbol);

                // Calculate change
                BigDecimal change = null;
                BigDecimal changePercent = null;
                if (quote.previousClose() != null && quote.previousClose().compareTo(BigDecimal.ZERO) > 0) {
                    change = quote.price().subtract(quote.previousClose());
                    changePercent = change.divide(quote.previousClose(), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                }

                IndexQuoteData quoteData = new IndexQuoteData(
                        indexKey,
                        symbol,
                        quote.price(),
                        change,
                        changePercent,
                        quote.currency(),
                        Instant.now()
                );

                // Store latest quote
                indexQuotes.put(indexKey, quoteData);

                // Store in history (keep last 100)
                LinkedList<QuoteHistory> history = indexHistory.get(indexKey);
                history.add(new QuoteHistory(quote.price(), Instant.now()));
                if (history.size() > 100) {
                    history.removeFirst();
                }

                indices.put(indexKey, new IndexQuoteResponse(
                        indexKey,
                        quote.price(),
                        change,
                        changePercent,
                        quote.currency()
                ));
            } catch (Exception e) {
                // Gracefully skip unavailable indices
                // Return cached data if available
                IndexQuoteData cached = indexQuotes.get(indexKey);
                if (cached != null) {
                    indices.put(indexKey, new IndexQuoteResponse(
                            indexKey,
                            cached.price,
                            cached.change,
                            cached.changePercent,
                            cached.currency
                    ));
                }
            }
        }

        return new MarketOverviewResponse(indices);
    }

    /**
     * Get historical quotes for an index
     */
    public List<QuoteHistory> getIndexHistory(String indexKey) {
        LinkedList<QuoteHistory> history = indexHistory.get(indexKey);
        if (history == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(history);
    }

    /**
     * Manually update an index quote (used by Kafka listener or polling)
     */
    public void updateIndexQuote(String indexKey, String symbol, BigDecimal price, BigDecimal previousClose) {
        if (!INDEX_SYMBOLS.containsKey(indexKey)) {
            return;
        }

        // Calculate change
        BigDecimal change = null;
        BigDecimal changePercent = null;
        if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) > 0) {
            change = price.subtract(previousClose);
            changePercent = change.divide(previousClose, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        IndexQuoteData quoteData = new IndexQuoteData(
                indexKey,
                symbol,
                price,
                change,
                changePercent,
                "USD",
                Instant.now()
        );

        // Store latest quote
        indexQuotes.put(indexKey, quoteData);

        // Store in history
        LinkedList<QuoteHistory> history = indexHistory.get(indexKey);
        history.add(new QuoteHistory(price, Instant.now()));
        if (history.size() > 100) {
            history.removeFirst();
        }
    }

    // Inner classes
    public static class IndexQuoteData {
        public String indexKey;
        public String symbol;
        public BigDecimal price;
        public BigDecimal change;
        public BigDecimal changePercent;
        public String currency;
        public Instant timestamp;

        public IndexQuoteData(String indexKey, String symbol, BigDecimal price, BigDecimal change,
                             BigDecimal changePercent, String currency, Instant timestamp) {
            this.indexKey = indexKey;
            this.symbol = symbol;
            this.price = price;
            this.change = change;
            this.changePercent = changePercent;
            this.currency = currency;
            this.timestamp = timestamp;
        }
    }

    public static class QuoteHistory {
        public BigDecimal price;
        public Instant timestamp;

        public QuoteHistory(BigDecimal price, Instant timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }
}
