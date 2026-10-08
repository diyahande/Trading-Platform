package com.capstone.controller;

import com.capstone.dto.WatchlistItemResponse;
import com.capstone.entity.Instrument;
import com.capstone.exception.InvalidInputException;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.service.InstrumentService;
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
    private final InstrumentService instrumentService;

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
        Long instrumentId = resolveInstrumentId(request);

        String name = request.get("name") instanceof String value
            ? value
            : null;

        if (name == null || name.isBlank()) {
            name = request.get("symbol") instanceof String value
                ? value.trim().toUpperCase()
                : "Watchlist";
        }

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

    private Long resolveInstrumentId(Map<String, Object> request) {
        Object instrumentIdValue = request.get("instrumentId");
        if (instrumentIdValue instanceof Number number) {
            return number.longValue();
        }

        Object symbolValue = request.get("symbol");
        if (!(symbolValue instanceof String symbol) || symbol.isBlank()) {
            throw new InvalidInputException();
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        Instrument instrument = instrumentService.getInstrumentBySymbol(normalizedSymbol)
                .orElseThrow(() -> new InstrumentNotFoundException(normalizedSymbol));

        return instrument.getInstrumentId();
    }
}
