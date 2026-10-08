package com.capstone.controller;

import com.capstone.dto.TopMoverResponse;
import com.capstone.service.TopMoversService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for Top Movers
 * Provides top gainers and losers based on market data
 */
@RestController
@RequestMapping("/api/top-movers")
@RequiredArgsConstructor
public class TopMoversController {

    private final TopMoversService topMoversService;

    /**
     * Get top 5 gainers
     * GET /api/top-movers/gainers
     */
    @GetMapping("/gainers")
    public ResponseEntity<List<TopMoverResponse>> getTopGainers() {
        return ResponseEntity.ok(topMoversService.getTopGainers());
    }

    /**
     * Get top 5 losers
     * GET /api/top-movers/losers
     */
    @GetMapping("/losers")
    public ResponseEntity<List<TopMoverResponse>> getTopLosers() {
        return ResponseEntity.ok(topMoversService.getTopLosers());
    }
}
