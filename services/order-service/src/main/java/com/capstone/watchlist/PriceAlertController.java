package com.capstone.watchlist;

import com.capstone.dto.CreatePriceAlertRequest;
import com.capstone.dto.PriceAlertResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for price alerts.
 * All routes require bearer token authentication via Spring Security filter.
 * Authorization: every route verifies the authenticated account owns the resource.
 */
@RestController
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    /**
     * Create a price alert for a watchlist item.
     * 
     * POST /api/v1/alerts
     * {
     *   "watchlistId": 123,
     *   "threshold": 150.00,
     *   "direction": "ABOVE"
     * }
     */
    @PostMapping("/api/v1/alerts")
    public ResponseEntity<PriceAlertResponse> createAlert(
            @Valid @RequestBody CreatePriceAlertRequest request,
            Authentication authentication
    ) {
        String accountReference = extractAccountReference(authentication);
        PriceAlertResponse response = priceAlertService.createAlert(accountReference, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all alerts for the authenticated account.
     * 
     * GET /api/v1/alerts
     */
    @GetMapping("/api/v1/alerts")
    public ResponseEntity<List<PriceAlertResponse>> getAlertsForAccount(
            Authentication authentication
    ) {
        String accountReference = extractAccountReference(authentication);
        List<PriceAlertResponse> alerts = priceAlertService.getAlertsForAccount(accountReference);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get all alerts for a specific watchlist.
     * Nested resource pattern: /api/v1/watchlist/{watchlistId}/alerts
     * 
     * GET /api/v1/watchlist/{watchlistId}/alerts
     */
    @GetMapping("/api/v1/watchlist/{watchlistId}/alerts")
    public ResponseEntity<List<PriceAlertResponse>> getAlertsForWatchlist(
            @PathVariable Long watchlistId,
            Authentication authentication
    ) {
        String accountReference = extractAccountReference(authentication);
        List<PriceAlertResponse> alerts = priceAlertService.getAlertsForWatchlist(
                accountReference,
                watchlistId
        );
        return ResponseEntity.ok(alerts);
    }

    /**
     * Cancel (deactivate) an alert.
     * 
     * DELETE /api/v1/alerts/{alertId}
     */
    @DeleteMapping("/api/v1/alerts/{alertId}")
    public ResponseEntity<Void> deactivateAlert(
            @PathVariable Long alertId,
            Authentication authentication
    ) {
        String accountReference = extractAccountReference(authentication);
        priceAlertService.deactivateAlert(accountReference, alertId);
        return ResponseEntity.noContent().build();
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
