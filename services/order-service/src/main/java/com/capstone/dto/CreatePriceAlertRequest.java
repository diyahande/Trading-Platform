package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePriceAlertRequest {

    @NotNull
    @JsonProperty("watchlistId")
    private Long watchlistId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @JsonProperty("threshold")
    private BigDecimal threshold;

    @NotBlank
    @JsonProperty("direction")
    private String direction;  // "ABOVE" or "BELOW"
}
