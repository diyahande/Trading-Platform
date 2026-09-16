package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A holding position for an account.
 * Maps to OpenAPI PositionResponse schema.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponse {

    /**
     * Account reference UUID
     */
    private String accountId;

    /**
     * Instrument symbol
     */
    private String symbol;

    /**
     * Net held quantity (never negative, short selling out of scope)
     */
    private Integer quantity;

    /**
     * Weighted average cost basis per unit.
     * A buy recalculates it; a sell reduces quantity and leaves it unchanged,
     * making realised P&L computable later.
     */
    private BigDecimal averageCost;
}
