package com.capstone.controller;

import com.capstone.dto.HealthResponseDto;
import com.capstone.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final PortfolioService portfolioService;

    @GetMapping("/health")
    public ResponseEntity<HealthResponseDto> getHealth() {
        return ResponseEntity.ok(portfolioService.getHealth());
    }
}
