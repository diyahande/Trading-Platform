package com.capstone.service.signal;

import com.capstone.dto.CandleDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class CandleCache {

    private static class Entry {
        private final List<CandleDataResponse> candles;
        private final Instant cachedAt;

        private Entry(List<CandleDataResponse> candles) {
            this.candles = candles;
            this.cachedAt = Instant.now();
        }
    }

    private final Duration ttl;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CandleCache(@Value("${app.signals.candle-cache-ttl-hours:24}") long ttlHours) {
        this.ttl = Duration.ofHours(Math.max(1L, ttlHours));
    }

    public Optional<List<CandleDataResponse>> get(String symbol) {
        Entry entry = cache.get(symbol);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.cachedAt.plus(ttl).isBefore(Instant.now())) {
            cache.remove(symbol);
            log.debug("Candle cache expired for symbol={}", symbol);
            return Optional.empty();
        }

        return Optional.of(entry.candles);
    }

    public void put(String symbol, List<CandleDataResponse> candles) {
        cache.put(symbol, new Entry(candles));
    }

    public void clear() {
        cache.clear();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("entries", cache.size());
        stats.put("ttlHours", ttl.toHours());
        stats.put("symbols", cache.keySet());
        return stats;
    }
}
