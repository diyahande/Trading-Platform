package com.capstone.entity;

import com.capstone.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaction {

    private final Long transactionId;
    private final UUID orderId;
    private final Long instrumentId;
    private final BigDecimal quantity;

    // Actual price achieved during execution.
    private final BigDecimal price;

    private final Instant executedAt;
    private final TransactionStatus status;

    public Transaction(
            Long transactionId,
            UUID orderId,
            Long instrumentId,
            BigDecimal quantity,
            BigDecimal price,
            Instant executedAt,
            TransactionStatus status) {

        this.transactionId = transactionId;
        this.orderId = orderId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.price = price;
        this.executedAt = executedAt;
        this.status = status;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getTransactionValue() {
        return quantity.multiply(price);
    }
}