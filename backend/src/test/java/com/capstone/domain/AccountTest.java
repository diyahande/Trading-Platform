package com.capstone.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void accountShouldStartWithActiveStatus() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    void debitShouldReduceCashBalance() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        account.debit(new BigDecimal("250.00"));

        assertEquals(
                new BigDecimal("750.00"),
                account.getCashBalance()
        );
    }

    @Test
    void creditShouldIncreaseCashBalance() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        account.credit(new BigDecimal("250.00"));

        assertEquals(
                new BigDecimal("1250.00"),
                account.getCashBalance()
        );
    }

    @Test
    void accountShouldAffordAmountEqualToCashBalance() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        assertTrue(account.canAfford(new BigDecimal("1000.00")));
    }

    @Test
    void accountShouldNotAffordAmountGreaterThanCashBalance() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        assertFalse(account.canAfford(new BigDecimal("1000.01")));
    }

    @Test
    void debitShouldRefuseOperationThatMakesBalanceNegative() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> account.debit(new BigDecimal("1000.01"))
        );

        assertEquals(
                new BigDecimal("1000.00"),
                account.getCashBalance()
        );
    }

    @Test
    void moneyShouldRemainAccurateAfterManyOperations() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00")
        );

        for (int i = 0; i < 1000; i++) {
            account.debit(new BigDecimal("0.01"));
            account.credit(new BigDecimal("0.01"));
        }

        assertEquals(
                new BigDecimal("1000.00"),
                account.getCashBalance()
        );
    }
}