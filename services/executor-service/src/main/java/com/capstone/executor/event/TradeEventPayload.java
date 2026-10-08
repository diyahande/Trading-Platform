package com.capstone.executor.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

// Trade event payload: ORDER_FILLED or ORDER_REJECTED for trade-events topic
public class TradeEventPayload {
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
    @JsonProperty("executedPrice")
    private BigDecimal executedPrice;
    @JsonProperty("status")
    private String status;
    @JsonProperty("reason")
    private String reason;
    @JsonProperty("cashDelta")
    private BigDecimal cashDelta;
    @JsonProperty("positionQuantityAfter")
    private Integer positionQuantityAfter;
    @JsonProperty("averageCostAfter")
    private BigDecimal averageCostAfter;
    @JsonProperty("executedOn")
    private Instant executedOn;
    
    public TradeEventPayload() {}
    public TradeEventPayload(String orderId, Long accountId, String symbol, String side, Integer quantity, BigDecimal price, BigDecimal executedPrice, String status, String reason, BigDecimal cashDelta, Integer positionQuantityAfter, BigDecimal averageCostAfter, Instant executedOn) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.executedPrice = executedPrice;
        this.status = status;
        this.reason = reason;
        this.cashDelta = cashDelta;
        this.positionQuantityAfter = positionQuantityAfter;
        this.averageCostAfter = averageCostAfter;
        this.executedOn = executedOn;
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
    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getCashDelta() { return cashDelta; }
    public void setCashDelta(BigDecimal cashDelta) { this.cashDelta = cashDelta; }
    public Integer getPositionQuantityAfter() { return positionQuantityAfter; }
    public void setPositionQuantityAfter(Integer positionQuantityAfter) { this.positionQuantityAfter = positionQuantityAfter; }
    public BigDecimal getAverageCostAfter() { return averageCostAfter; }
    public void setAverageCostAfter(BigDecimal averageCostAfter) { this.averageCostAfter = averageCostAfter; }
    public Instant getExecutedOn() { return executedOn; }
    public void setExecutedOn(Instant executedOn) { this.executedOn = executedOn; }
}
