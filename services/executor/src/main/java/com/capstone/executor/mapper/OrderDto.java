package com.capstone.executor.mapper;

import java.math.BigDecimal;
import java.time.Instant;

// MyBatis DTO for orders table
public class OrderDto {
    private String orderId;
    private Long accountId;
    private String accountReference;
    private Long instrumentId;
    private String side;
    private Integer quantity;
    private BigDecimal price;
    private String status;
    private String idempotencyKey;
    private Instant createdAt;
    private BigDecimal executedPrice;
    private Instant executedOn;
    private String rejectionReason;
    
    public OrderDto() {}
    public OrderDto(String orderId, Long accountId, String accountReference, Long instrumentId, String side, Integer quantity, BigDecimal price, String status, String idempotencyKey, Instant createdAt, BigDecimal executedPrice, Instant executedOn, String rejectionReason) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.accountReference = accountReference;
        this.instrumentId = instrumentId;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.executedPrice = executedPrice;
        this.executedOn = executedOn;
        this.rejectionReason = rejectionReason;
    }
    
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getAccountReference() { return accountReference; }
    public void setAccountReference(String accountReference) { this.accountReference = accountReference; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }
    public Instant getExecutedOn() { return executedOn; }
    public void setExecutedOn(Instant executedOn) { this.executedOn = executedOn; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
