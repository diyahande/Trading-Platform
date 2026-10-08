package com.capstone.service.signal;

import com.capstone.dto.CandleDataResponse;
import com.capstone.dto.SignalIndicatorsResponse;
import com.capstone.dto.SignalMetricsResponse;
import com.capstone.dto.SignalPricePointResponse;
import com.capstone.dto.SignalResponse;
import com.capstone.dto.SignalScoresResponse;
import com.capstone.exception.MarketDataUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signal generation and caching service.
 * 
 * Coordinates:
 * 1. Fetching historical candle data (with caching)
 * 2. Computing technical indicators
 * 3. Applying deterministic scoring strategy
 * 4. Caching computed signals with validity windows
 * 5. Tracking audit trail
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignalService {

     private static final Duration SIGNAL_VALIDITY = Duration.ofMinutes(5);
     private static final Duration ERROR_VALIDITY = Duration.ofMinutes(1);
     private static final String METHODOLOGY = "SMA20-SMA50-RSI14-MOM10-v1";
    
    private final CandleCache candleCache;
     private final FauxnanceClient fauxnanceClient;
     private final TechnicalIndicatorCalculator indicatorCalculator;
     private final SignalStrategy signalStrategy;
    
    private static class CachedSignal {
        SignalResponse signal;
        Instant cachedAt;
        
        CachedSignal(SignalResponse signal) {
            this.signal = signal;
            this.cachedAt = Instant.now();
        }
    }
    
    private final Map<String, CachedSignal> signalCache = new ConcurrentHashMap<>();
    
    /**
     * Get cached signal or compute if needed.
     * Signals are cached for their validity window (typically 5 minutes).
     */
    public SignalResponse getSignal(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        // Check cache
        CachedSignal cached = signalCache.get(normalizedSymbol);
        if (cached != null && Instant.now().isBefore(cached.signal.getValidUntil())) {
            updateStaleness(cached.signal);
            log.debug("Signal cache hit for symbol={}, age={}ms", normalizedSymbol,
                Instant.now().toEpochMilli() - cached.cachedAt.toEpochMilli());
            return cached.signal;
        }
        
        if (cached != null) {
            log.debug("Signal cache expired for symbol={}", normalizedSymbol);
        }
        
        // Not in cache or expired - compute
        return computeAndCacheSignal(normalizedSymbol);
    }
    
    /**
     * Force recomputation and caching of signal.
     * Called by scheduled task every 5 minutes.
     */
    public SignalResponse computeAndCacheSignal(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        log.info("Computing signal for symbol={}", normalizedSymbol);
        
        try {
            // Fetch candles (with caching)
            List<CandleDataResponse> candles = fetchCandles(normalizedSymbol);
            if (candles == null || candles.isEmpty()) {
                log.warn("Cannot compute signal for symbol={}: no candle data available", normalizedSymbol);
                return createUnavailableResponse(
                        normalizedSymbol,
                        "DATA_UNAVAILABLE",
                        "Historical candle data is unavailable"
                );
            }

            SignalIndicatorsResponse indicators;
            try {
                indicators = indicatorCalculator.calculate(candles);
            } catch (IllegalArgumentException ex) {
                log.warn("Cannot compute signal for symbol={}: {}", normalizedSymbol, ex.getMessage());
                return createUnavailableResponse(
                        normalizedSymbol,
                        "INSUFFICIENT_DATA",
                        ex.getMessage()
                );
            }

            SignalStrategy.StrategyResult strategyResult = signalStrategy.evaluate(indicators);

            Instant now = Instant.now();
            Instant validUntil = now.plus(SIGNAL_VALIDITY);
            
            SignalResponse response = new SignalResponse();
            response.setSymbol(normalizedSymbol);
            response.setStatus("OK");
            response.setSignal(strategyResult.signal());
            response.setDirection(strategyResult.signal());
            response.setStrength(strategyResult.strength());
            response.setRationale(strategyResult.rationale());
            response.setScores(new SignalScoresResponse(strategyResult.buyScore(), strategyResult.sellScore()));
            response.setIndicators(indicators);
            response.setReasons(new ArrayList<>(strategyResult.reasons()));
            response.setPriceHistory90d(extractPriceHistory90d(candles));
            response.setComputedAt(now);
            response.setValidUntil(validUntil);
            response.setMetrics(toLegacyMetrics(indicators));
            response.setMethodology(METHODOLOGY);
            updateStaleness(response);
            
            // Cache
            signalCache.put(normalizedSymbol, new CachedSignal(response));
            log.info("Signal cached for symbol={}, validUntil={}", normalizedSymbol, validUntil);
            
            return response;
            
        } catch (MarketDataUnavailableException e) {
            log.error("Market data unavailable for symbol={}: {}", normalizedSymbol, e.getMessage(), e);
            return createUnavailableResponse(
                normalizedSymbol,
                "DATA_UNAVAILABLE",
                e.getMessage()
            );
        } catch (Exception e) {
            log.error("Error computing signal for symbol={}: {}", normalizedSymbol, e.getMessage(), e);
            return createUnavailableResponse(
                    normalizedSymbol,
                    "DATA_UNAVAILABLE",
                    "Signal computation failed due to data/network error"
            );
        }
    }
    
    /**
     * Fetch candles from cache or Fauxnance API.
     */
    private List<CandleDataResponse> fetchCandles(String symbol) {
        // Try cache first
        Optional<List<CandleDataResponse>> cached = candleCache.get(symbol);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Cache miss - fetch from Fauxnance
        log.debug("Fetching candles from Fauxnance for symbol={}", symbol);
        List<CandleDataResponse> candleList = fauxnanceClient.fetchDailyCandles(symbol);
        if (candleList == null || candleList.isEmpty()) {
            return List.of();
        }

        candleCache.put(symbol, candleList);
        log.info("Fetched {} candles from Fauxnance for symbol={}", candleList.size(), symbol);
        return candleList;
    }
    
    /**
     * Update staleness indicator based on time until expiration.
     */
    private void updateStaleness(SignalResponse response) {
        long secondsUntilExpiry = Instant.now().until(response.getValidUntil(), java.time.temporal.ChronoUnit.SECONDS);
        
        if (secondsUntilExpiry <= 0) {
            response.setStaleness("STALE");
        } else if (secondsUntilExpiry < 30) {
            response.setStaleness("AGING");
        } else {
            response.setStaleness("FRESH");
        }
    }
    
    /**
     * Create error/fallback signal.
     */
    private SignalResponse createUnavailableResponse(String symbol, String status, String reason) {
        SignalResponse response = new SignalResponse();
        response.setSymbol(symbol);
        response.setStatus(status);
        response.setSignal("HOLD");
        response.setDirection("HOLD");
        response.setStrength(0);
        response.setRationale(reason + ". Deterministic signal is not generated when required data is missing.");
        response.setScores(new SignalScoresResponse(0, 0));
        response.setReasons(List.of(reason));
        response.setPriceHistory90d(List.of());
        response.setComputedAt(Instant.now());
        response.setValidUntil(Instant.now().plus(ERROR_VALIDITY));
        response.setMethodology(METHODOLOGY);
        response.setStaleness("STALE");
        return response;
    }

    private SignalMetricsResponse toLegacyMetrics(SignalIndicatorsResponse indicators) {
        SignalMetricsResponse metrics = new SignalMetricsResponse();
        metrics.setPrice(indicators.getCurrentPrice());
        metrics.setPriceChange(indicators.getCurrentPrice().subtract(indicators.getClose10DaysAgo()).setScale(4, RoundingMode.HALF_UP));
        metrics.setPriceChangePercent(indicators.getMomentum10d());
        metrics.setMa20(indicators.getSma20());
        metrics.setMa50(indicators.getSma50());
        metrics.setRsi14(indicators.getRsi14());
        metrics.setMomentum10d(indicators.getMomentum10d());
        return metrics;
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.strip().toUpperCase();
    }

    private List<SignalPricePointResponse> extractPriceHistory90d(List<CandleDataResponse> candles) {
        List<CandleDataResponse> validCandles = candles.stream()
                .filter(candle -> candle != null
                        && candle.getDate() != null
                        && candle.getClose() != null
                        && candle.getClose().signum() > 0)
                .sorted(Comparator.comparing(CandleDataResponse::getDate))
            .toList();

        return validCandles.stream()
            .skip(Math.max(0, validCandles.size() - 90L))
                .map(candle -> new SignalPricePointResponse(candle.getDate(), candle.getClose()))
                .toList();
    }
    
    /**
     * Get cache statistics for monitoring.
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cachedSignals", signalCache.size());
        stats.put("symbols", signalCache.keySet());
        stats.put("candleCache", candleCache.getStats());
        stats.put("timestamp", Instant.now());
        return stats;
    }
    
    /**
     * Clear all caches (useful for testing).
     */
    public void clearCaches() {
        signalCache.clear();
        candleCache.clear();
        log.info("Signal caches cleared");
    }
}
