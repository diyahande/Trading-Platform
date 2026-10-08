package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PnlResponseDto {

    private String accountId;
    private String baseCurrency;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal realisedPnl;
    private BigDecimal unrealisedPnl;
    private BigDecimal totalPnl;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<SymbolPnlDto> bySymbol;

    private OffsetDateTime asOf;
}
