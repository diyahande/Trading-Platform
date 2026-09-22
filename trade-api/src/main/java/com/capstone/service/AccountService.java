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

    public AccountResponse getAccountResponseForAuthorizedAccount(String accountReference, String accountKey) {
        Account account = verifyAuthorizedAccount(accountReference, accountKey);

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


    public BalanceResponse getBalanceResponseForAuthorizedAccount(String accountReference, String accountKey) {
        Account account = verifyAuthorizedAccount(accountReference, accountKey);
        BigDecimal balance = getAccountBalance(account.getAccountId());

        BalanceResponse response = new BalanceResponse();
        response.setAccountId(account.getAccountReference());
        response.setCashBalance(balance);
        response.setCurrency("USD");
        response.setAsOf(LocalDateTime.now());

        return response;
    }


    public List<PositionResponse> getPositionsForAuthorizedAccount(String accountReference, String accountKey) {
        Account account = verifyAuthorizedAccount(accountReference, accountKey);
        return getPositionsByAccountId(account.getAccountId());
    }

    // Retrieve account by ID without strict validation.
    public Optional<Account> getAccountById(Long accountId) {
        return accountMapper.findAccountById(accountId);
    }


    public Account resolveAccountFromToken(String accountKey) {
        Account account = accountMapper.findAccountByReference(accountKey)
                .orElseThrow(() -> new AccountNotFoundException(-1L));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getAccountId(), account.getStatus());
        }

        return account;
    }

    public Account verifyAuthorizedAccount(String requestedAccountReference, String accountKey) {
        Account requestedAccount = accountMapper.findAccountByReference(requestedAccountReference)
                .orElseThrow(() -> new AccountNotFoundException(-1L));

        if (requestedAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(requestedAccount.getAccountId(), requestedAccount.getStatus());
        }

        // Keep ACC-403 uniform for suspended or unreachable accounts.
        if (!requestedAccount.getAccountReference().equals(accountKey)) {
            throw new AccountNotActiveException(requestedAccount.getAccountId(), requestedAccount.getStatus());
        }

        return requestedAccount;
    }

    public Account getAccountByReferenceStrict(String accountReference) {
        Account account = accountMapper.findAccountByReference(accountReference)
            .orElseThrow(() -> new AccountNotFoundException(-1L));

        if (account.getStatus() != null) {
            String statusName = account.getStatus().name();
            if (!statusName.equalsIgnoreCase("ACTIVE")) {
                throw new AccountNotActiveException(account.getAccountId(), account.getStatus());
            }
        }

        return account;
    }

 
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

    public BigDecimal getAccountBalance(Long accountId) {
        Account account = getAccountByIdStrict(accountId);
        return account.getCashBalance();
    }

  
    public void updateCashBalance(Long accountId, BigDecimal newBalance, Long version) {
        int rowsAffected = accountMapper.updateCashBalanceWithVersion(accountId, newBalance, version);

        if (rowsAffected == 0) {
            throw new com.capstone.exception.ConcurrentModificationException(accountId, version);
        }
    }

 
    public List<PositionResponse> getPositionsByAccountId(Long accountId) {
        // Verify account exists first
        getAccountByIdStrict(accountId);
        
        return accountMapper.findPositionsByAccountId(accountId);
    }

    public boolean accountExists(Long accountId) {
        return accountMapper.findAccountById(accountId).isPresent();
    }
    public boolean isAccountActive(Long accountId) {
        try {
            getAccountByIdStrict(accountId);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    
    public Account getAccountDetails(Long accountId) {
        return getAccountByIdStrict(accountId);
    }
}
