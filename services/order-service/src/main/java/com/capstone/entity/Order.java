package com.capstone.entity;

import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Order {

    private final UUID orderId;
    private final Long accountId;
    private final String accountReference;
    private final Long instrumentId;
    private final OrderSide side;
    private final BigDecimal quantity;

    // Customer-submitted limit price.
    private final BigDecimal price;

    private OrderStatus status;

    private final String idempotencyKey;
    private final Instant createdAt;

    public Order(
            UUID orderId,
            Long accountId,
            String accountReference,
            Long instrumentId,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal price,
            OrderStatus status,
            String idempotencyKey,
            Instant createdAt) {

        this.orderId = orderId;
        this.accountId = accountId;
        this.accountReference = accountReference;
        this.instrumentId = instrumentId;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getAccountReference() {
        return accountReference;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public OrderSide getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getOrderValue() {
        return price.multiply(quantity);
    }

    public boolean isBuy() {
        return side == OrderSide.BUY;
    }

    public boolean isSell() {
        return side == OrderSide.SELL;
    }

    public void markFilled() {
        ensureNew();

        status = OrderStatus.FILLED;
    }

    public void reject() {
        ensureNew();

        status = OrderStatus.REJECTED;
    }

    public void cancel() {
        ensureNew();

        status = OrderStatus.CANCELLED;
    }

    private void ensureNew() {

        if (status != OrderStatus.NEW) {
            throw new IllegalStateException(
                    "Order cannot transition from "
                            + status);
        }
    }
}