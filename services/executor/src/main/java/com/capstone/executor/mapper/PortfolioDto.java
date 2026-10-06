package com.capstone.executor.mapper;



/**
 * Simple DTO for portfolio table - just holds the portfolio_id.
 */
public class PortfolioDto {
    private Long portfolioId;
    private Long accountId;
    
    public PortfolioDto() {}
    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
}
