package com.capstone.controller;

import com.capstone.dto.AccountResponse;
import com.capstone.dto.BalanceResponse;
import com.capstone.dto.PositionResponse;
import com.capstone.service.AccountService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * GET /api/v1/accounts/{id}
     * Returns account details.
     * 
     * Auth: Token's account key must match requested account ID (ACC-403 if not).
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(
                accountService.getAccountResponseForAuthorizedAccount(id, authentication.getName())
        );
    }

    /**
     * GET /api/v1/accounts/{id}/balance
     * Returns cash balance.
     * 
     * Auth: Token's account key must match requested account ID (ACC-403 if not).
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(
            accountService.getBalanceResponseForAuthorizedAccount(id, authentication.getName())
        );
    }

    /**
     * GET /api/v1/accounts/{id}/positions
     * Returns holdings.
     * 
     * Auth: Token's account key must match requested account ID (ACC-403 if not).
     */
    @GetMapping("/{id}/positions")
    public ResponseEntity<List<PositionResponse>> getPositions(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(
                accountService.getPositionsForAuthorizedAccount(id, authentication.getName())
        );
    }
}

