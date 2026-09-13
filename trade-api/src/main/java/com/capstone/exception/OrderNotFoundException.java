package com.capstone.exception;

public final class OrderNotFoundException extends DomainException {

    private final long orderId;

    public OrderNotFoundException(long orderId) {
        super("ORD-404", "No order exists with that identifier");
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}