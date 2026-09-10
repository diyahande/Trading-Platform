package com.capstone.controller;

import com.capstone.dto.AccountResponse;
import com.capstone.dto.BalanceResponse;
import com.capstone.dto.PositionResponse;
import com.capstone.dto.ErrorResponse;
import com.capstone.entity.Account;
import com.capstone.service.AccountService;
import com.capstone.filter.JwtAuthenticationFilter;
import com.capstone.exception.AccountNotFoundException;
import com.capstone.exception.AccountNotActiveException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<?> getAccount(
            @PathVariable Long id,
            HttpServletRequest request) {

        try {
            String accountKey = JwtAuthenticationFilter.getAccountKeyFromRequest(request);
            
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
        } catch (AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("ACC-404", "Account not found"));
        } catch (AccountNotActiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createErrorResponse("ACC-403", "Account not accessible"));
        }
    }

    /**
     * GET /api/v1/accounts/{id}/balance
     * Returns cash balance.
     * 
     * Auth: Token's account key must match requested account ID (ACC-403 if not).
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<?> getBalance(
            @PathVariable Long id,
            HttpServletRequest request) {

        try {
            String accountKey = JwtAuthenticationFilter.getAccountKeyFromRequest(request);
            
            // Verify authorization: requested account must match token
            accountService.verifyAuthorizedAccount(id, accountKey);

            BigDecimal balance = accountService.getAccountBalance(id);

            BalanceResponse response = new BalanceResponse();

            response.setAccountId(id);
            response.setCashBalance(balance);
            response.setCurrency("USD");
            response.setAsOf(LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("ACC-404", "Account not found"));
        } catch (AccountNotActiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createErrorResponse("ACC-403", "Account not accessible"));
        }
    }

    /**
     * GET /api/v1/accounts/{id}/positions
     * Returns holdings.
     * 
     * Auth: Token's account key must match requested account ID (ACC-403 if not).
     */
    @GetMapping("/{id}/positions")
    public ResponseEntity<?> getPositions(
            @PathVariable Long id,
            HttpServletRequest request) {

        try {
            String accountKey = JwtAuthenticationFilter.getAccountKeyFromRequest(request);
            
            // Verify authorization: requested account must match token
            accountService.verifyAuthorizedAccount(id, accountKey);

            List<PositionResponse> positions = accountService.getPositionsByAccountId(id);

            return ResponseEntity.ok(positions);
        } catch (AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("ACC-404", "Account not found"));
        } catch (AccountNotActiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createErrorResponse("ACC-403", "Account not accessible"));
        }
    }

    private ErrorResponse createErrorResponse(String code, String message) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(code);
        response.setMessage(message);
        return response;
    }
}

