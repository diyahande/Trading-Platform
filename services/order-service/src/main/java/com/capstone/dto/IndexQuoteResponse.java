package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for a single index quote
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexQuoteResponse {
    @JsonProperty("indexKey")
    private String indexKey;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("change")
    private BigDecimal change;

    @JsonProperty("changePercent")
    private BigDecimal changePercent;

    @JsonProperty("currency")
    private String currency;
}
