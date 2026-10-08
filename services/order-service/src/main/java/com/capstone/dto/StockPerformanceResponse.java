package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockPerformanceResponse {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("change")
    private BigDecimal change;

    @JsonProperty("changePercent")
    private BigDecimal changePercent;

    @JsonProperty("lastUpdated")
    private long lastUpdated;
}
