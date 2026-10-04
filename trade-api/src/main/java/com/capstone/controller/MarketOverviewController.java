package com.capstone.controller;

import com.capstone.dto.MarketOverviewResponse;
import com.capstone.service.MarketOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for Market Overview
 * Provides market index data and historical quotes
 */
@RestController
@RequestMapping("/api/market-overview")
@RequiredArgsConstructor
public class MarketOverviewController {

    private final MarketOverviewService marketOverviewService;

    /**
     * Get current market overview for all indices
     * GET /api/market-overview
     */
    @GetMapping
    public ResponseEntity<MarketOverviewResponse> getMarketOverview() {
        try {
            MarketOverviewResponse overview = marketOverviewService.getMarketOverview();
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            // Return partial data or error response
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Get historical quotes for a specific index
     * GET /api/market-overview/{indexKey}/history
     * Returns last 100 quotes for charting
     */
    @GetMapping("/{indexKey}/history")
    public ResponseEntity<HistoryResponse> getIndexHistory(@PathVariable String indexKey) {
        try {
            List<MarketOverviewService.QuoteHistory> history = marketOverviewService.getIndexHistory(indexKey);
            List<QuotePoint> points = history.stream()
                    .map(q -> new QuotePoint(q.price.doubleValue(), q.timestamp.toEpochMilli()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new HistoryResponse(indexKey, points));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Response class for historical data
     */
    public static class HistoryResponse {
        public String indexKey;
        public List<QuotePoint> history;

        public HistoryResponse(String indexKey, List<QuotePoint> history) {
            this.indexKey = indexKey;
            this.history = history;
        }
    }

    /**
     * Single quote point for charting
     */
    public static class QuotePoint {
        public double price;
        public long timestamp;

        public QuotePoint(double price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }
}
