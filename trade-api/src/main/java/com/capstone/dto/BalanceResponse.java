package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cash balance response.
 * Maps to OpenAPI BalanceResponse schema.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    /**
     * Account reference UUID
     */
    private String accountId;

    /**
     * Available cash balance to 2 decimal places.
     * Does not include market value of holdings.
     */
    private BigDecimal cashBalance;

    /**
     * ISO 4217 currency code (e.g., USD)
     */
    private String currency;

    /**
     * Timestamp when this balance was captured
     */
    private LocalDateTime asOf;
}
