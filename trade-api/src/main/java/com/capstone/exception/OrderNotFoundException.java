package com.capstone.exception;

import java.util.UUID;

public final class OrderNotFoundException extends DomainException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("ORD-404", "No order exists with that identifier");
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}