package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItemResponse {

    private Long watchlistId;
    private String symbol;
    private String displayName;
    private String assetClass;
    private BigDecimal currentPrice;
    private BigDecimal change;
    private BigDecimal changePercent;
    private Instant addedAt;
}
