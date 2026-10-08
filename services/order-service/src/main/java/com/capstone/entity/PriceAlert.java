package com.capstone.entity;

import java.math.BigDecimal;
import java.time.Instant;

public class PriceAlert {

    public enum Direction {
        ABOVE,
        BELOW
    }

    public enum Status {
        ACTIVE,
        TRIGGERED,
        DEACTIVATED,
        DELIVERY_FAILED
    }

    private final Long alertId;
    private final Long watchlistId;
    private final BigDecimal threshold;
    private final Direction direction;
    private final Status status;
    private final Instant createdAt;
    private final Instant triggeredAt;
    private final Instant updatedAt;

    public PriceAlert(
            Long alertId,
            Long watchlistId,
            BigDecimal threshold,
            Direction direction,
            Status status,
            Instant createdAt,
            Instant triggeredAt,
            Instant updatedAt) {

        this.alertId = alertId;
        this.watchlistId = watchlistId;
        this.threshold = threshold;
        this.direction = direction;
        this.status = status;
        this.createdAt = createdAt;
        this.triggeredAt = triggeredAt;
        this.updatedAt = updatedAt;
    }

    public Long getAlertId() {
        return alertId;
    }

    public Long getWatchlistId() {
        return watchlistId;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public Direction getDirection() {
        return direction;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
