package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private BigDecimal portfolioValue;
    private BigDecimal todaysPnl;
    private BigDecimal availableCash;
    private BigDecimal buyingPower;
}