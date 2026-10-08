package com.capstone.controller;

import com.capstone.dto.PnlResponseDto;
import com.capstone.dto.PortfolioSummaryDto;
import com.capstone.dto.PricedPositionDto;
import com.capstone.exception.AccountNotAccessibleException;
import com.capstone.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/{accountRef}")
    public ResponseEntity<PortfolioSummaryDto> getPortfolioSummary(
            @PathVariable String accountRef,
            Authentication authentication) {
        assertAccountAccessible(accountRef, authentication);
        return ResponseEntity.ok(portfolioService.getPortfolioSummary(accountRef));
    }

    @GetMapping("/{accountRef}/positions")
    public ResponseEntity<List<PricedPositionDto>> getPricedPositions(
            @PathVariable String accountRef,
            @RequestParam(required = false) String symbol,
            Authentication authentication) {
        assertAccountAccessible(accountRef, authentication);
        return ResponseEntity.ok(portfolioService.getPricedPositions(accountRef, symbol));
    }

    @GetMapping("/{accountRef}/pnl")
    public ResponseEntity<PnlResponseDto> getPnl(
            @PathVariable String accountRef,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean bySymbol,
            Authentication authentication) {
        assertAccountAccessible(accountRef, authentication);
        return ResponseEntity.ok(portfolioService.getPnl(accountRef, from, to, bySymbol));
    }

    private void assertAccountAccessible(String accountRef, Authentication authentication) {
        String tokenAccountRef = extractTokenAccountRef(authentication);
        if (accountRef == null || tokenAccountRef == null || !accountRef.trim().equals(tokenAccountRef.trim())) {
            throw new AccountNotAccessibleException();
        }
    }

    private String extractTokenAccountRef(Authentication authentication) {
        String accountRef = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            accountRef = jwt.getClaimAsString("accountId");
        }
        if (accountRef == null || accountRef.isBlank()) {
            accountRef = authentication == null ? null : authentication.getName();
        }

        if (accountRef == null || accountRef.isBlank()) {
            throw new AccountNotAccessibleException();
        }

        return accountRef;
    }
}
