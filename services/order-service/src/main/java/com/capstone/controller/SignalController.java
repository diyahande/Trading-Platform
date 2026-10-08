package com.capstone.controller;

import com.capstone.dto.SignalResponse;
import jakarta.validation.constraints.Pattern;
import com.capstone.service.signal.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoint for trading signals.
 * 
 * Signals are precomputed every 5 minutes by SignalComputeTask
 * and cached by SignalService.
 * This endpoint returns cached signals with minimal latency.
 */
@RestController
@RequestMapping("/api/v1/signals")
@RequiredArgsConstructor
@Slf4j
@Validated
public class SignalController {
    
    private final SignalService signalService;
    
    /**
     * Get signal recommendation for a specific symbol.
     * 
     * Returns:
     * - direction: "BUY", "SELL", or "HOLD"
     * - strength: 0-100 confidence score
     * - rationale: Plain-English explanation
     * - computedAt: When signal was computed
     * - validUntil: When signal expires
     * - staleness: "FRESH", "AGING", or "STALE"
     * - metrics: Detailed indicators (MA20, MA50, RSI, price, etc.)
     * - methodology: Name of the algorithm used
     * 
     * Example request: GET /api/v1/signals/AAPL
     * 
     * @param symbol Stock ticker symbol (e.g., "AAPL")
     * @return SignalResponse with recommendation
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<SignalResponse> getSignal(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9._-]{1,15}$", message = "Invalid symbol format")
            String symbol) {
        log.info("Signal requested for symbol={}", symbol);
        
        SignalResponse signal = signalService.getSignal(symbol.toUpperCase());
        return ResponseEntity.ok(signal);
    }
    
    /**
     * Health check and monitoring endpoint.
     * Returns cache statistics and status.
     * 
     * Example request: GET /api/v1/signals/health
     * 
     * @return Cache statistics
     */
    @GetMapping("/health/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.debug("Signal service health check requested");
        
        Map<String, Object> stats = signalService.getCacheStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Force clear all caches (for testing/debugging only).
     * This will cause signals to be recomputed on next request.
     * 
     * @return Confirmation message
     */
    @PostMapping("/debug/clear-cache")
    public ResponseEntity<Map<String, String>> clearCache() {
        log.warn("Signal cache clear requested via debug endpoint");
        
        signalService.clearCaches();
        return ResponseEntity.ok(Map.of("message", "Signal caches cleared"));
    }
}
