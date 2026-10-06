package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private Long id;

    private String accountId;

    private String holderName;

    private BigDecimal cashBalance;

    private String status;

 
    private Integer version;

    private OffsetDateTime lastUpdated;
}
