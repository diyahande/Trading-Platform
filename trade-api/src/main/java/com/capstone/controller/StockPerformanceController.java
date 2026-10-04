package com.capstone.controller;

import com.capstone.dto.StockPerformanceResponse;
import com.capstone.service.StockPerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Stock Performance Controller
 * Endpoints for market visualization dashboard
 */
@RestController
@RequestMapping("/api/market-performance")
@RequiredArgsConstructor
public class StockPerformanceController {

    private final StockPerformanceService stockPerformanceService;

    /**
     * GET /api/market-performance
     * Returns all monitored stocks with performance metrics
     * Sorted by changePercent (gainers first)
     */
    @GetMapping
    public ResponseEntity<List<StockPerformanceResponse>> getAllStockPerformance() {
        return ResponseEntity.ok(stockPerformanceService.getAllStockPerformance());
    }

    /**
     * GET /api/market-performance/visualization
     * Returns performance data with statistics for heatmap/grid visualization
     */
    @GetMapping("/visualization")
    public ResponseEntity<Map<String, Object>> getPerformanceVisualization() {
        return ResponseEntity.ok(stockPerformanceService.getPerformanceVisualization());
    }
}
