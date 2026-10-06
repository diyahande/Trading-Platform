package com.capstone.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Portfolio {

    private final Long portfolioId;
    private final Long accountId;
    private BigDecimal currentValue;
    private BigDecimal investedValue;
    private Instant updatedAt;

    private final List<PortfolioHolding> holdings = new ArrayList<>();

    public Portfolio(
            Long portfolioId,
            Long accountId,
            BigDecimal currentValue,
            BigDecimal investedValue,
            Instant updatedAt) {

        this.portfolioId = portfolioId;
        this.accountId = accountId;
        this.currentValue = currentValue;
        this.investedValue = investedValue;
        this.updatedAt = updatedAt;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public BigDecimal getInvestedValue() {
        return investedValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void addHolding(PortfolioHolding holding) {
        holdings.add(holding);
    }

    public PortfolioHolding getHolding(Long instrumentId) {
        return holdings.stream()
                .filter(holding -> holding.getInstrumentId().equals(instrumentId))
                .findFirst()
                .orElse(null);
    }
}