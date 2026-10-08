package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

  
    private String orderId;

  
    private String status;

  
    private String message;

    private String symbol;

    private String side;

  
    private Integer quantity;

    private BigDecimal price;
}
