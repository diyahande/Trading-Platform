package com.capstone.exception;

import java.util.UUID;

public final class OrderNotCancellableException extends DomainException {

    private final UUID orderId;

    public OrderNotCancellableException(UUID orderId) {
        super("ORD-409", "The order is not in a cancellable state");
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}

