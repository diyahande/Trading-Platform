package com.capstone.service;

import com.capstone.dto.AccountResponse;
import com.capstone.dto.BalanceResponse;
import com.capstone.entity.Account;
import com.capstone.dto.PositionResponse;
import com.capstone.exception.AccountNotActiveException;
import com.capstone.exception.AccountNotFoundException;
import com.capstone.exception.DomainException;
import com.capstone.mapper.AccountMapper;
import com.capstone.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

// Account Service handles all account-related business logic.

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountMapper accountMapper;

    /**
     * Retrieve account details for an authorized caller.
     */
    public AccountResponse getAccountResponseForAuthorizedAccount(Long accountId, String accountKey) {
        verifyAuthorizedAccount(accountId, accountKey);
        Account account = getAccountByIdStrict(accountId);

        AccountResponse response = new AccountResponse();
        response.setId(account.getAccountId());
        response.setAccountId(account.getAccountReference());
        response.setHolderName(account.getHolderName());
        response.setCashBalance(account.getCashBalance());
        response.setStatus(account.getStatus().name());
        response.setVersion(account.getVersion().intValue());
        response.setLastUpdated(LocalDateTime.ofInstant(account.getCreatedAt(), ZoneId.systemDefault()));

        return response;
    }

    /**
     * Retrieve balance details for an authorized caller.
     */
    public BalanceResponse getBalanceResponseForAuthorizedAccount(Long accountId, String accountKey) {
        verifyAuthorizedAccount(accountId, accountKey);
        BigDecimal balance = getAccountBalance(accountId);

        BalanceResponse response = new BalanceResponse();
        response.setAccountId(accountId);
        response.setCashBalance(balance);
        response.setCurrency("USD");
        response.setAsOf(LocalDateTime.now());

        return response;
    }

    /**
     * Retrieve positions for an authorized caller.
     */
    public List<PositionResponse> getPositionsForAuthorizedAccount(Long accountId, String accountKey) {
        verifyAuthorizedAccount(accountId, accountKey);
        return getPositionsByAccountId(accountId);
    }

    // Retrieve account by ID without strict validation.
    public Optional<Account> getAccountById(Long accountId) {
        return accountMapper.findAccountById(accountId);
    }

    /**
     * Resolve account from JWT token's account key (reference/username).
     * Throws AccountNotFoundException if account doesn't exist (ACC-404).
     * Throws AccountNotActiveException if account is not active (ACC-403).
     */
    public Account resolveAccountFromToken(String accountKey) {
        Account account = accountMapper.findAccountByReference(accountKey)
                .orElseThrow(() -> new AccountNotFoundException(-1L));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getAccountId(), account.getStatus());
        }

        return account;
    }

    /**
     * Verify the requested account matches the authenticated account key.
     * Throws AccountNotFoundException if requested account doesn't exist (ACC-404).
     * Throws AccountNotActiveException if requested account is not active (ACC-403).
     * Throws AccountNotActiveException if account key doesn't match requested account (ACC-403).
     */
    public Account verifyAuthorizedAccount(Long requestedAccountId, String accountKey) {
        Account requestedAccount = accountMapper.findAccountById(requestedAccountId)
                .orElseThrow(() -> new AccountNotFoundException(requestedAccountId));

        if (requestedAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(requestedAccountId, requestedAccount.getStatus());
        }

        boolean matchesReference = requestedAccount.getAccountReference().equals(accountKey);
        boolean matchesNumericId = String.valueOf(requestedAccountId).equals(accountKey);

        // Keep ACC-403 uniform for suspended or unreachable accounts.
        if (!matchesReference && !matchesNumericId) {
            throw new AccountNotActiveException(requestedAccountId, requestedAccount.getStatus());
        }

        return requestedAccount;
    }

    /**
     * Retrieve account by ID with strict validation.
     * Throws exception if account doesn't exist or is not active.
     * Used by OrderService for validation.
     */
    public Account getAccountByIdStrict(Long accountId) {
        Account account = accountMapper.findAccountById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));

        // Validate account is active
        if (account.getStatus() != null) {
            String statusName = account.getStatus().name();
            if (!statusName.equalsIgnoreCase("ACTIVE")) {
                throw new AccountNotActiveException(accountId, account.getStatus());
            }
        }

        return account;
    }

    /**
     * Retrieve account balance (cash balance).
     */
    public BigDecimal getAccountBalance(Long accountId) {
        Account account = getAccountByIdStrict(accountId);
        return account.getCashBalance();
    }

    /**
     * Update account cash balance with optimistic locking.
     * Increments the version to detect concurrent modifications.
     * Throws ConcurrentModificationException if version mismatch is detected.
     */
    public void updateCashBalance(Long accountId, BigDecimal newBalance, Long version) {
        int rowsAffected = accountMapper.updateCashBalanceWithVersion(accountId, newBalance, version);

        if (rowsAffected == 0) {
            throw new com.capstone.exception.ConcurrentModificationException(accountId, version);
        }
    }

    /**
     * Retrieve all positions (holdings) for an account.
     * Returns a list of PositionResponse objects containing:
     * - holdingId
     * - portfolioId
     * - instrumentId
     * - quantity
     * - averagePrice
     * - lastUpdated
     */
    public List<PositionResponse> getPositionsByAccountId(Long accountId) {
        // Verify account exists first
        getAccountByIdStrict(accountId);
        
        return accountMapper.findPositionsByAccountId(accountId);
    }

    /**
     * Check if account exists (soft check, no exception).
     */
    public boolean accountExists(Long accountId) {
        return accountMapper.findAccountById(accountId).isPresent();
    }

    /**
     * Check if account is active.
     */
    public boolean isAccountActive(Long accountId) {
        try {
            getAccountByIdStrict(accountId);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    /**
     * Retrieve account details with all related data.
     */
    public Account getAccountDetails(Long accountId) {
        return getAccountByIdStrict(accountId);
    }
}
