package com.capstone.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class PortfolioHolding {

    private final Long holdingId;
    private final Long portfolioId;
    private final Long instrumentId;
    private BigDecimal quantity;
    private BigDecimal averagePrice;
    private Instant lastUpdated;

    public PortfolioHolding(
            Long holdingId,
            Long portfolioId,
            Long instrumentId,
            BigDecimal quantity,
            BigDecimal averagePrice,
            Instant lastUpdated) {

        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException(
                    "Holding quantity cannot be negative");
        }

        if (averagePrice == null || averagePrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Average price cannot be negative");
        }

        this.holdingId = holdingId;
        this.portfolioId = portfolioId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.lastUpdated = lastUpdated;
    }

    public Long getHoldingId() {
        return holdingId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    /*
     * BUY:
     *
     * New average =
     * (old quantity × old average price
     *  + new quantity × purchase price)
     * /
     * (old quantity + new quantity)
     */
    public void increaseQuantity(
            BigDecimal newQuantity,
            BigDecimal purchasePrice) {

        validatePositive(newQuantity);

        if (purchasePrice == null ||
                purchasePrice.signum() < 0) {

            throw new IllegalArgumentException(
                    "Purchase price cannot be negative");
        }

        BigDecimal oldValue =
                quantity.multiply(averagePrice);

        BigDecimal newValue =
                newQuantity.multiply(purchasePrice);

        BigDecimal totalQuantity =
                quantity.add(newQuantity);

        averagePrice = oldValue
                .add(newValue)
                .divide(
                        totalQuantity,
                        2,
                        RoundingMode.HALF_UP);

        quantity = totalQuantity;

        lastUpdated = Instant.now();
    }

    /*
     * SELL:
     *
     * Quantity decreases.
     * Average price DOES NOT change.
     */
    public void decreaseQuantity(
            BigDecimal sellQuantity) {

        validatePositive(sellQuantity);

        if (quantity.compareTo(sellQuantity) < 0) {
            throw new IllegalArgumentException(
                    "Holding quantity cannot become negative");
        }

        quantity = quantity.subtract(sellQuantity);

        // Average price deliberately remains unchanged.

        lastUpdated = Instant.now();
    }

    private static void validatePositive(
            BigDecimal value) {

        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }
    }
}