package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Client-facing signal response.
 * Contains direction, strength, explanation, and validity window.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalResponse {
    private String symbol;

    // Status metadata: OK, INSUFFICIENT_DATA, DATA_UNAVAILABLE, INVALID_INPUT
    private String status;

    // Primary signal field requested by API clients
    private String signal;  // "BUY", "SELL", or "HOLD"
    
    // Signal direction
    private String direction;  // "BUY", "SELL", or "HOLD"
    
    // Confidence score (0-100)
    private Integer strength;
    
    // Plain-English explanation of why this signal was generated
    private String rationale;

    // Score and indicator breakdowns for explainability
    private SignalScoresResponse scores;
    private SignalIndicatorsResponse indicators;
    private List<String> reasons;

    // Last 90 valid daily closes for chart rendering
    private List<SignalPricePointResponse> priceHistory90d;
    
    // Computation timestamp and validity window
    private Instant computedAt;
    private Instant validUntil;
    
    // Staleness indicator
    private String staleness;  // "FRESH", "AGING", or "STALE"
    
    // Detailed metrics for transparency
    private SignalMetricsResponse metrics;
    
    // Methodology identifier for audit trail
    private String methodology;  // e.g., "MA-Crossover-RSI-v1"
}
