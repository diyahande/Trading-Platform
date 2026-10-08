package com.capstone.executor.mapper;

import java.math.BigDecimal;
import java.time.Instant;

// MyBatis DTO for transactions table
public class TransactionDto {
    private Long transactionId;
    private String orderId;
    private Long instrumentId;
    private BigDecimal quantity;
    private BigDecimal price;
    private Instant executedAt;
    private String status;
    
    public TransactionDto() {}
    
    public TransactionDto(String orderId, Long instrumentId, BigDecimal quantity, BigDecimal price, String status) {
        this.orderId = orderId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
    }
    
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
