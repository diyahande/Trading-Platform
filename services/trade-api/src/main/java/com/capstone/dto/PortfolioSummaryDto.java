package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummaryDto {

    private String accountId;
    private String baseCurrency;
    private BigDecimal cashBalance;
    private BigDecimal marketValue;
    private BigDecimal costBasis;
    private BigDecimal unrealisedPnl;
    private BigDecimal unrealisedPnlPercent;
    private BigDecimal realisedPnl;
    private BigDecimal totalValue;
    private Integer positionCount;
    private boolean partial;
    private OffsetDateTime asOf;
}
