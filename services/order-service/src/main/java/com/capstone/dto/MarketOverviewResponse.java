package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for market overview containing all indices
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketOverviewResponse {
    @JsonProperty("indices")
    private Map<String, IndexQuoteResponse> indices;
}
