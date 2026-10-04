package com.capstone.controller;

import com.capstone.dto.WatchlistItemResponse;
import com.capstone.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public ResponseEntity<List<WatchlistItemResponse>> getWatchlist(Authentication authentication) {
        String accountReference = extractAccountReference(authentication);
        List<WatchlistItemResponse> watchlist = watchlistService.getWatchlistForAuthorizedAccount(accountReference);
        return ResponseEntity.ok(watchlist);
    }

    @PostMapping
    public ResponseEntity<Void> addToWatchlist(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {

        String accountReference = extractAccountReference(authentication);
        Long instrumentId = ((Number) request.get("instrumentId")).longValue();
        String name = (String) request.get("name");

        watchlistService.addToWatchlist(accountReference, instrumentId, name);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{watchlistId}")
    public ResponseEntity<Void> removeFromWatchlist(
            Authentication authentication,
            @PathVariable Long watchlistId) {

        String accountReference = extractAccountReference(authentication);
        watchlistService.removeFromWatchlist(accountReference, watchlistId);
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
