package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private String accountId;


    private BigDecimal cashBalance;

 
    private String currency;

    
    private OffsetDateTime asOf;
}
