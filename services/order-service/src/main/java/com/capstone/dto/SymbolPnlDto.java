package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymbolPnlDto {

    private String symbol;
    private BigDecimal realisedPnl;
    private BigDecimal unrealisedPnl;
    private BigDecimal totalPnl;
}
