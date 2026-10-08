package com.capstone.controller;

import com.capstone.dto.CustomerPreferenceResponse;
import com.capstone.dto.FxRateResponse;
import com.capstone.dto.UpdatePreferenceRequest;
import com.capstone.service.CustomerPreferenceService;
import com.capstone.service.FxConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
public class CustomerPreferenceController {

    private final CustomerPreferenceService customerPreferenceService;
    private final FxConversionService fxConversionService;

    @GetMapping
    public ResponseEntity<CustomerPreferenceResponse> getPreferences(Authentication authentication) {
        String accountReference = extractAccountReference(authentication);
        return ResponseEntity.ok(customerPreferenceService.getPreferences(accountReference));
    }

    @PutMapping
    public ResponseEntity<CustomerPreferenceResponse> updatePreferences(
            Authentication authentication,
            @RequestBody UpdatePreferenceRequest request) {
        String accountReference = extractAccountReference(authentication);
        return ResponseEntity.ok(customerPreferenceService.updatePreferences(accountReference, request));
    }

    @GetMapping("/fx-rate")
    public ResponseEntity<FxRateResponse> getFxRate(
            @RequestParam String from,
            @RequestParam String to) {
        if (from.equalsIgnoreCase(to)) {
            return ResponseEntity.ok(new FxRateResponse(from.toUpperCase(), to.toUpperCase(), BigDecimal.ONE));
        }
        BigDecimal rate = fxConversionService.convert(BigDecimal.ONE, from, to);
        return ResponseEntity.ok(new FxRateResponse(from.toUpperCase(), to.toUpperCase(), rate));
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
