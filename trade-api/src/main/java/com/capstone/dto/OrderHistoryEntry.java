package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistoryEntry {

    private String orderId;

    private String accountId;

   
    private String symbol;

    private String side;

  
    private Integer quantity;

   
    private BigDecimal price;

 
    private BigDecimal executedPrice;

  
    private String status;

  
    private String idempotencyKey;

 
    
    private OffsetDateTime createdOn;
}
