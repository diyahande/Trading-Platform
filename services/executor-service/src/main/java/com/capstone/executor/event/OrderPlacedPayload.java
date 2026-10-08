package com.capstone.executor.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

// ORDER_PLACED event payload from orders topic
public class OrderPlacedPayload {
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("accountId")
    private Long accountId;
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("side")
    private String side;
    @JsonProperty("quantity")
    private Integer quantity;
    @JsonProperty("price")
    private BigDecimal price;
    @JsonProperty("idempotencyKey")
    private String idempotencyKey;
    @JsonProperty("createdOn")
    private Instant createdOn;
    
    public OrderPlacedPayload() {}
    public OrderPlacedPayload(String orderId, Long accountId, String symbol, String side, Integer quantity, BigDecimal price, String idempotencyKey, Instant createdOn) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
        this.createdOn = createdOn;
    }
    
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedOn() { return createdOn; }
    public void setCreatedOn(Instant createdOn) { this.createdOn = createdOn; }
}
