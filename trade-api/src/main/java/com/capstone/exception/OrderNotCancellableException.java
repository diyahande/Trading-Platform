package com.capstone.exception;

public final class OrderNotCancellableException extends DomainException {

    private final long orderId;

    public OrderNotCancellableException(long orderId) {
        super("ORD-409", "The order is not in a cancellable state");
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}

