package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricedPositionDto {

    private String accountId;
    private String symbol;
    private Integer quantity;
    private BigDecimal averageCost;
    private BigDecimal costBasis;
    private BigDecimal lastPrice;
    private BigDecimal marketValue;
    private BigDecimal unrealisedPnl;
    private BigDecimal unrealisedPnlPercent;
    private String currency;
    private OffsetDateTime priceAsOf;
    private boolean stale;
}
