package com.capstone.controller;

import com.capstone.dto.DashboardResponse;
import com.capstone.dto.PortfolioAllocationResponse;
import com.capstone.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(Authentication authentication) {
        String accountReference = extractAccountReference(authentication);
        return ResponseEntity.ok(
                dashboardService.getDashboardSummaryForAuthorizedAccount(accountReference)
        );
    }

    @GetMapping("/portfolio-allocation")
    public ResponseEntity<PortfolioAllocationResponse> getPortfolioAllocation(Authentication authentication) {
        String accountReference = extractAccountReference(authentication);
        return ResponseEntity.ok(
                dashboardService.getPortfolioAllocationForAuthorizedAccount(accountReference)
        );
    }

    private String extractAccountReference(Authentication authentication) {
        String accountReference = authentication.getName();

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String accountIdClaim = jwt.getClaimAsString("accountId");
            if (accountIdClaim != null && !accountIdClaim.isBlank()) {
                accountReference = accountIdClaim;
            }
        }

        return accountReference;
    }
}