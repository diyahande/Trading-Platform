package com.capstone.executor.mapper;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * MyBatis DTO for portfolio_holdings table.
 * Represents a single position in a portfolio.
 */
public class PortfolioHoldingsDto {
    private Long holdingId;
    private Long portfolioId;
    private Long instrumentId;
    private BigDecimal quantity;
    private BigDecimal averagePrice;
    private Long version;
    private Instant lastUpdated;
    
    public PortfolioHoldingsDto() {}
    
    public Long getHoldingId() { return holdingId; }
    public void setHoldingId(Long holdingId) { this.holdingId = holdingId; }
    
    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
    
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    
    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
