package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalMetricsResponse {
    private BigDecimal price;
    private BigDecimal priceChange;
    private BigDecimal priceChangePercent;
    private BigDecimal ma20;
    private BigDecimal ma50;
    private BigDecimal rsi14;
    private BigDecimal momentum10d;
}
