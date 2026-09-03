//Checks if an account is behaving correctly 
package com.capstone.domain;

import com.capstone.entity.Account;
import com.capstone.enums.AccountStatus;
import com.capstone.exception.InsufficientFundsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;

class AccountTest {

    private static Account account;

    @BeforeEach
    void setUp() {
        account = new Account(
                1L,
                100L,
                "ACC-001",
                "Alice Morgan",
                "USD",
                new BigDecimal("1000.00"),
                AccountStatus.ACTIVE,
                1L,
                Instant.now()
        );
    }

    @AfterEach
    void tearDown() {
        account=null;
    }

    @Test
    void accountShouldStartWithActiveStatus() {
        assertEquals(
                AccountStatus.ACTIVE,
                account.getStatus()
        );
    }

    @Test
    void debitShouldReduceCashBalance() {
        account.debit(new BigDecimal("250.00"));

        assertEquals(
                new BigDecimal("750.00"),
                account.getCashBalance()
        );
    }

    @Test
    void creditShouldIncreaseCashBalance() {
        account.credit(new BigDecimal("250.00"));

        assertEquals(
                new BigDecimal("1250.00"),
                account.getCashBalance()
        );
    }

    @Test
    void accountShouldAffordAmountEqualToCashBalance() {
        assertTrue(
                account.canAfford(new BigDecimal("1000.00"))
        );
    }

    @Test
    void accountShouldNotAffordAmountGreaterThanCashBalance() {
        assertFalse(
                account.canAfford(new BigDecimal("1000.01"))
        );
    }

    @Test
    void debitShouldRefuseOperationThatMakesBalanceNegative() {
        assertThrows(
                InsufficientFundsException.class,
                () -> account.debit(new BigDecimal("1000.01"))
        );

        assertEquals(
                new BigDecimal("1000.00"),
                account.getCashBalance()
        );
    }

    @Test
    void moneyShouldRemainAccurateAfterManyOperations() {
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


