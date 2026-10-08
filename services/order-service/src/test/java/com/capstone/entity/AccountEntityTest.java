package com.capstone.entity;

import com.capstone.enums.AccountStatus;
import com.capstone.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AccountEntityTest {

    @Test
    void constructor_rejectsInvalidCashBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, 1L, "acc", "h", "USD", null, AccountStatus.ACTIVE, 1L, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, 1L, "acc", "h", "USD", new BigDecimal("-1"), AccountStatus.ACTIVE, 1L, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, 1L, "acc", "h", "USD", new BigDecimal("1.12345"), AccountStatus.ACTIVE, 1L, Instant.now()));
    }

    @Test
    void isActive_and_canAfford_behaveAsExpected() {
        Account account = account(new BigDecimal("50.00"));

        assertTrue(account.isActive());
        assertTrue(account.canAfford(new BigDecimal("50.00")));
        assertFalse(account.canAfford(new BigDecimal("50.01")));
    }

    @Test
    void canAfford_validatesAmount() {
        Account account = account(new BigDecimal("50.00"));

        assertThrows(IllegalArgumentException.class, () -> account.canAfford(null));
        assertThrows(IllegalArgumentException.class, () -> account.canAfford(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> account.canAfford(new BigDecimal("1.12345")));
    }

    @Test
    void credit_updatesBalanceRoundedToTwo() {
        Account account = account(new BigDecimal("10.0000"));

        account.credit(new BigDecimal("2.5555"));

        assertEquals(new BigDecimal("12.56"), account.getCashBalance());
    }

    @Test
    void debit_validatesAndUpdatesBalance() {
        Account account = account(new BigDecimal("10.0000"));

        account.debit(new BigDecimal("2.5555"));

        assertEquals(new BigDecimal("7.44"), account.getCashBalance());
    }

    @Test
    void debit_insufficientFunds_throwsDomainException() {
        Account account = account(new BigDecimal("3.00"));

        assertThrows(InsufficientFundsException.class, () -> account.debit(new BigDecimal("4.00")));
    }

    @Test
    void getters_returnConstructorValues() {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        Account account = new Account(9L, 2L, "acc-9", "Name", "USD", new BigDecimal("1.00"), AccountStatus.CLOSED, 8L, now);

        assertEquals(9L, account.getAccountId());
        assertEquals(2L, account.getUserId());
        assertEquals("acc-9", account.getAccountReference());
        assertEquals("Name", account.getHolderName());
        assertEquals("USD", account.getCurrency());
        assertEquals(AccountStatus.CLOSED, account.getStatus());
        assertEquals(8L, account.getVersion());
        assertEquals(now, account.getCreatedAt());
    }

    private static Account account(BigDecimal balance) {
        return new Account(
                1L,
                1L,
                "acc-1",
                "Holder",
                "USD",
                balance,
                AccountStatus.ACTIVE,
                1L,
                Instant.parse("2024-01-01T00:00:00Z")
        );
    }
}


