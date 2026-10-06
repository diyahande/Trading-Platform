package com.capstone.dto;
import com.capstone.enums.*;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PlaceOrderRequest {

    @NotBlank
    @Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
        message = "accountId must be a valid UUID"
    )
    private String accountId;

    @NotBlank
    @Size(max = 20)
    private String symbol;

   @NotNull
   private OrderSide side;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;

    @NotNull
    @Size(min = 8, max = 100)
    private String idempotencyKey;


    public PlaceOrderRequest(
            String accountId,
            String symbol,
            OrderSide side,
            Integer quantity,
            BigDecimal price,
            String idempotencyKey) {

        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

   public OrderSide getSide() {
       return side;
   }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
