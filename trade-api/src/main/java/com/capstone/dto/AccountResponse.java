package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private Long id;

    private String accountId;

    private String holderName;

    private BigDecimal cashBalance;

    private String status;

    /**
     * Optimistic lock version.
     * Increments on every account update.
     */
    private Integer version;

    /**
     * Last update timestamp.
     */
    private LocalDateTime lastUpdated;
}
