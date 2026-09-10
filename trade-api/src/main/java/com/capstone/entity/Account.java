package com.capstone.entity;


import com.capstone.enums.AccountStatus;
import com.capstone.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class Account {

    private final Long accountId;
    private final Long userId;
    private final String accountReference;
    private final String holderName;
    private final String currency;
    private BigDecimal cashBalance;
    private AccountStatus status;
    private final Long version;
    private final Instant createdAt;

    public Account(
            Long accountId,
            Long userId,
            String accountReference,
            String holderName,
            String currency,
            BigDecimal cashBalance,
            AccountStatus status,
            Long version,
            Instant createdAt) {

        this.accountId = accountId;
        this.userId = userId;
        this.accountReference = accountReference;
        this.holderName = holderName;
        this.currency = currency;
        this.cashBalance = money(cashBalance);
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAccountReference() {
        return accountReference;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean canAfford(BigDecimal amount) {
        validateAmount(amount);
        return cashBalance.compareTo(amount) >= 0;
    }

    public void credit(BigDecimal amount) {
        validateAmount(amount);

        cashBalance = cashBalance
                .add(amount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public void debit(BigDecimal amount) {
        validateAmount(amount);

        if (!canAfford(amount)) {
            throw new InsufficientFundsException(
                    accountId,
                    amount,
                    cashBalance
            );
        }

        cashBalance = cashBalance
                .subtract(amount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Amount cannot be negative");
        }

        if (amount.scale() > 4) {
            throw new IllegalArgumentException(
                    "Money must have at most four decimal places");
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException(
                    "Cash balance cannot be null");
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Cash balance cannot be negative");
        }

        if (amount.scale() > 4) {
            throw new IllegalArgumentException(
                    "Money must have at most four decimal places");
        }

        return amount.setScale(4, RoundingMode.HALF_UP);
    }
}