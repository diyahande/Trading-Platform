package com.capstone.controller;

import com.capstone.dto.AccountResponse;
import com.capstone.dto.BalanceResponse;
import com.capstone.dto.PositionResponse;
import com.capstone.entity.Account;
import com.capstone.service.AccountService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

        String accountKey = authentication.getName();

        // Verify authorization: requested account must match token
        accountService.verifyAuthorizedAccount(id, accountKey);

        Account account = accountService.getAccountByIdStrict(id);

        AccountResponse response = new AccountResponse();

        response.setId(account.getAccountId());
        response.setAccountId(account.getAccountReference());
        response.setHolderName(account.getHolderName());
        response.setCashBalance(account.getCashBalance());
        response.setStatus(account.getStatus().name());

        // Account domain uses Long, contract DTO uses Integer (int32)
        response.setVersion(account.getVersion().intValue());

        response.setLastUpdated(
            LocalDateTime.ofInstant(
                account.getCreatedAt(),
                ZoneId.systemDefault()
            )
        );

        return ResponseEntity.ok(response);
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

        String accountKey = authentication.getName();

        // Verify authorization: requested account must match token
        accountService.verifyAuthorizedAccount(id, accountKey);

        BigDecimal balance = accountService.getAccountBalance(id);

        BalanceResponse response = new BalanceResponse();

        response.setAccountId(id);
        response.setCashBalance(balance);
        response.setCurrency("USD");
        response.setAsOf(LocalDateTime.now());

        return ResponseEntity.ok(response);
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

        String accountKey = authentication.getName();

        // Verify authorization: requested account must match token
        accountService.verifyAuthorizedAccount(id, accountKey);

        List<PositionResponse> positions = accountService.getPositionsByAccountId(id);

        return ResponseEntity.ok(positions);
    }
}

