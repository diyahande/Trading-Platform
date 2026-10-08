package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlertResponse {

    @JsonProperty("alertId")
    private Long alertId;

    @JsonProperty("watchlistId")
    private Long watchlistId;

    @JsonProperty("threshold")
    private BigDecimal threshold;

    @JsonProperty("direction")
    private String direction;  // "ABOVE" or "BELOW"

    @JsonProperty("status")
    private String status;  // "ACTIVE", "TRIGGERED", "DEACTIVATED", "DELIVERY_FAILED"

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("triggeredAt")
    private Instant triggeredAt;  // null if not triggered

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
