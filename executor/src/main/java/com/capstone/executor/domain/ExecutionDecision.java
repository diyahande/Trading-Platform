package com.capstone.executor.domain;

import java.math.BigDecimal;

// Immutable result of the pure execution decision.
public class ExecutionDecision {

    public enum Type {
        FILL,
        REJECT
    }

    private final Type type;
    private final BigDecimal executionPrice;
    private final String rejectReason;

    private ExecutionDecision(
            Type type,
            BigDecimal executionPrice,
            String rejectReason) {

        this.type = type;
        this.executionPrice = executionPrice;
        this.rejectReason = rejectReason;
    }

    public static ExecutionDecision fill(BigDecimal executionPrice) {

        if (executionPrice == null) {
            throw new IllegalArgumentException(
                    "execution price cannot be null for fill"
            );
        }

        return new ExecutionDecision(
                Type.FILL,
                executionPrice,
                null
        );
    }

    public static ExecutionDecision reject(String reason) {

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "rejection reason cannot be null or empty"
            );
        }

        return new ExecutionDecision(
                Type.REJECT,
                null,
                reason
        );
    }

    public boolean isFill() {
        return type == Type.FILL;
    }

    public boolean isReject() {
        return type == Type.REJECT;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}