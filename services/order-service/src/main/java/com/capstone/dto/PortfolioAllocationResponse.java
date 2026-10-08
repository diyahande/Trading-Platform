package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioAllocationResponse {

    private BigDecimal totalPortfolioValue;
    private BigDecimal cashValue;
    private Map<String, BigDecimal> allocationByAssetClass; // e.g., {"EQUITY": 50.5, "CRYPTO": 25.3, "FOREX": 24.2}
}
