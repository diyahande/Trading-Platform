package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalIndicatorsResponse {
    private BigDecimal currentPrice;
    private BigDecimal close10DaysAgo;
    private BigDecimal sma20;
    private BigDecimal sma50;
    private BigDecimal rsi14;
    private BigDecimal momentum10d;
}
