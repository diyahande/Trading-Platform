package com.capstone.service;

import com.capstone.entity.Account;
import com.capstone.dto.PositionResponse;
import com.capstone.exception.AccountNotActiveException;
import com.capstone.exception.AccountNotFoundException;
import com.capstone.exception.DomainException;
import com.capstone.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * AccountService handles all account-related business logic.
 * Provides account queries, balance operations, and position retrieval.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountMapper accountMapper;

    /**
     * Retrieve account by ID without strict validation.
     * Returns Optional which may be empty if account doesn't exist.
     */
    public Optional<Account> getAccountById(Long accountId) {
        return accountMapper.findAccountById(accountId);
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
     * Update account cash balance.
     */
    public void updateCashBalance(Long accountId, BigDecimal newBalance) {
        accountMapper.updateCashBalance(accountId, newBalance);
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
