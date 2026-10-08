package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioHoldingRow {

    private Long accountId;
    private String symbol;
    private Integer quantity;
    private BigDecimal averageCost;
    private String currency;
}
