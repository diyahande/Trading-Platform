package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandleDataResponse {
    private LocalDate date;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    @JsonAlias("adjclose")
    private BigDecimal adjClose;
    private BigDecimal volume;

    // Calculated fields
    private BigDecimal dailyReturn;
    private BigDecimal priceRange;
    private BigDecimal priceRangePct;
}
