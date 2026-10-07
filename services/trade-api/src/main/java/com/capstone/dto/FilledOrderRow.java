package com.capstone.dto;

import com.capstone.enums.OrderSide;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilledOrderRow {

    private String symbol;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal executedPrice;
    private OffsetDateTime executedOn;
}
