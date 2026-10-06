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

   
    @GetMapping("/{accountRef}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountRef,
            Authentication authentication) {
        return ResponseEntity.ok(
                accountService.getAccountResponseForAuthorizedAccount(accountRef, authentication.getName())
        );
    }

 
    @GetMapping("/{accountRef}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String accountRef,
            Authentication authentication) {
        return ResponseEntity.ok(
            accountService.getBalanceResponseForAuthorizedAccount(accountRef, authentication.getName())
        );
    }

 
    @GetMapping("/{accountRef}/positions")
    public ResponseEntity<List<PositionResponse>> getPositions(
            @PathVariable String accountRef,
            Authentication authentication) {
        return ResponseEntity.ok(
                accountService.getPositionsForAuthorizedAccount(accountRef, authentication.getName())
        );
    }
}

