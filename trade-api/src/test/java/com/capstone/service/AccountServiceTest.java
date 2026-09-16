package com.capstone.service;

import com.capstone.dto.PositionResponse;
import com.capstone.entity.Account;
import com.capstone.enums.AccountStatus;
import com.capstone.exception.AccountNotActiveException;
import com.capstone.exception.AccountNotFoundException;
import com.capstone.exception.ConcurrentModificationException;
import com.capstone.mapper.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountMapper accountMapper;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountMapper);
    }

    @Test
    void getAccountById_returnsOptional() {
        Account account = account(1L, "acc-1", AccountStatus.ACTIVE, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.of(account));

        Optional<Account> result = accountService.getAccountById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getAccountId());
    }

    @Test
    void resolveAccountFromToken_notFound_throws() {
        when(accountMapper.findAccountByReference("missing")).thenReturn(Optional.empty());

        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> accountService.resolveAccountFromToken("missing")
        );

        assertEquals("ACC-404", ex.getCatalogueCode());
        assertEquals(-1L, ex.getAccountId());
    }

    @Test
    void resolveAccountFromToken_inactive_throws() {
        Account account = account(1L, "acc-1", AccountStatus.SUSPENDED, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountByReference("acc-1")).thenReturn(Optional.of(account));

        AccountNotActiveException ex = assertThrows(
                AccountNotActiveException.class,
                () -> accountService.resolveAccountFromToken("acc-1")
        );

        assertEquals("ACC-403", ex.getCatalogueCode());
        assertEquals(1L, ex.getAccountId());
    }

    @Test
    void resolveAccountFromToken_active_returnsAccount() {
        Account account = account(1L, "acc-1", AccountStatus.ACTIVE, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountByReference("acc-1")).thenReturn(Optional.of(account));

        Account result = accountService.resolveAccountFromToken("acc-1");

        assertEquals(account, result);
    }

    @Test
    void verifyAuthorizedAccount_notFound_throws() {
        when(accountMapper.findAccountByReference("missing-ref")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.verifyAuthorizedAccount("missing-ref", "missing-ref"));
    }

    @Test
    void verifyAuthorizedAccount_inactive_throws() {
        Account account = account(5L, "acc-5", AccountStatus.CLOSED, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountByReference("acc-5")).thenReturn(Optional.of(account));

        assertThrows(AccountNotActiveException.class, () -> accountService.verifyAuthorizedAccount("acc-5", "acc-5"));
    }

    @Test
    void verifyAuthorizedAccount_mismatch_throws() {
        Account account = account(5L, "acc-5", AccountStatus.ACTIVE, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountByReference("acc-5")).thenReturn(Optional.of(account));

        assertThrows(AccountNotActiveException.class, () -> accountService.verifyAuthorizedAccount("acc-5", "someone-else"));
    }

    @Test
    void verifyAuthorizedAccount_referenceMatch_returnsAccount() {
        Account account = account(5L, "acc-5", AccountStatus.ACTIVE, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountByReference("acc-5")).thenReturn(Optional.of(account));

        Account result = accountService.verifyAuthorizedAccount("acc-5", "acc-5");

        assertEquals(account, result);
    }

    @Test
    void getAccountByIdStrict_notFound_throws() {
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.getAccountByIdStrict(1L));
    }

    @Test
    void getAccountByIdStrict_statusNull_returns() {
        Account account = account(1L, "acc-1", null, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.of(account));

        Account result = accountService.getAccountByIdStrict(1L);

        assertEquals(account, result);
    }

    @Test
    void getAccountByIdStrict_statusNotActive_throws() {
        Account account = account(1L, "acc-1", AccountStatus.SUSPENDED, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.of(account));

        assertThrows(AccountNotActiveException.class, () -> accountService.getAccountByIdStrict(1L));
    }

    @Test
    void getAccountByIdStrict_active_returns() {
        Account account = account(1L, "acc-1", AccountStatus.ACTIVE, new BigDecimal("100.00"), 1L);
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.of(account));

        Account result = accountService.getAccountByIdStrict(1L);

        assertEquals(account, result);
    }

    @Test
    void getAccountBalance_returnsBalance() {
        Account account = account(1L, "acc-1", AccountStatus.ACTIVE, new BigDecimal("88.50"), 1L);
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.of(account));

        BigDecimal result = accountService.getAccountBalance(1L);

        assertEquals(new BigDecimal("88.5000"), result);
    }

    @Test
    void updateCashBalance_success() {
        when(accountMapper.updateCashBalanceWithVersion(1L, new BigDecimal("10.00"), 3L)).thenReturn(1);

        assertDoesNotThrow(() -> accountService.updateCashBalance(1L, new BigDecimal("10.00"), 3L));
    }

    @Test
    void updateCashBalance_conflict_throws() {
        when(accountMapper.updateCashBalanceWithVersion(1L, new BigDecimal("10.00"), 3L)).thenReturn(0);

        assertThrows(ConcurrentModificationException.class,
                () -> accountService.updateCashBalance(1L, new BigDecimal("10.00"), 3L));
    }

    @Test
    void getPositionsByAccountId_validatesAndReturnsPositions() {
        Account account = account(1L, "acc-1", AccountStatus.ACTIVE, new BigDecimal("100.00"), 1L);
        PositionResponse position = new PositionResponse("11111111-1111-4111-8111-111111111111", "AAPL", 10, new BigDecimal("101.25"));
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.of(account));
        when(accountMapper.findPositionsByAccountId(1L)).thenReturn(List.of(position));

        List<PositionResponse> result = accountService.getPositionsByAccountId(1L);

        assertEquals(1, result.size());
        assertEquals("AAPL", result.get(0).getSymbol());
    }

    @Test
    void accountExists_trueWhenPresent() {
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.of(account(1L, "acc", AccountStatus.ACTIVE, BigDecimal.ONE, 1L)));

        assertTrue(accountService.accountExists(1L));
    }

    @Test
    void accountExists_falseWhenMissing() {
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.empty());

        assertFalse(accountService.accountExists(1L));
    }

    @Test
    void isAccountActive_trueForValidAccount() {
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.of(account(1L, "acc", AccountStatus.ACTIVE, BigDecimal.ONE, 1L)));

        assertTrue(accountService.isAccountActive(1L));
    }

    @Test
    void isAccountActive_falseForDomainException() {
        when(accountMapper.findAccountById(1L)).thenReturn(Optional.empty());

        assertFalse(accountService.isAccountActive(1L));
    }

    @Test
    void getAccountDetails_delegatesStrictLookup() {
        Account account = account(2L, "acc-2", AccountStatus.ACTIVE, new BigDecimal("44.00"), 2L);
        when(accountMapper.findAccountById(2L)).thenReturn(Optional.of(account));

        Account result = accountService.getAccountDetails(2L);

        assertEquals(account, result);
    }

    private static Account account(Long id, String ref, AccountStatus status, BigDecimal balance, Long version) {
        return new Account(
                id,
                10L,
                ref,
                "Holder",
                "USD",
                balance,
                status,
                version,
                Instant.parse("2024-01-01T00:00:00Z")
        );
    }
}


