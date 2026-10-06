package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for a top mover (gainer or loser)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopMoverResponse {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("assetClass")
    private String assetClass;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("change")
    private BigDecimal change;

    @JsonProperty("changePercent")
    private BigDecimal changePercent;
}
