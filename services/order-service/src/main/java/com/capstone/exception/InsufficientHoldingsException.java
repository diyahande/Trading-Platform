package com.capstone.exception;

import java.math.BigDecimal;

public final class InsufficientHoldingsException extends DomainException {

    private final long accountId;
    private final String symbol;
    private final BigDecimal requestedQuantity;
    private final BigDecimal availableQuantity;

    public InsufficientHoldingsException(
            long accountId,
            String symbol,
            BigDecimal requestedQuantity,
            BigDecimal availableQuantity) {

        super("ORD-409", "Insufficient holdings");

        this.accountId = accountId;
        this.symbol = symbol;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getRequestedQuantity() {
        return requestedQuantity;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }
}
