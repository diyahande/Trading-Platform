package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.Instant;

// MyBatis mapper for orders table with guarded state transitions
public interface OrderMapper {
    
    // Find order by ID; returns null if not found
    @Select("SELECT order_id, account_id, account_reference, instrument_id, side, quantity, price, status, idempotency_key, created_at, executed_price, executed_on, rejection_reason FROM orders WHERE order_id = #{orderId}::uuid")
    OrderDto findById(@Param("orderId") String orderId);
    
    // Update to FILLED: returns 1 if successful, 0 if not NEW (idempotent guard)
    @Update("UPDATE orders SET status = 'FILLED', executed_price = #{executedPrice}, executed_on = #{executedOn} WHERE order_id = #{orderId}::uuid AND status = 'NEW'")
    int updateToFilled(@Param("orderId") String orderId, @Param("executedPrice") BigDecimal executedPrice, @Param("executedOn") Instant executedOn);
    
    // Update to REJECTED: returns 1 if successful, 0 if not NEW (idempotent guard)
    @Update("UPDATE orders SET status = 'REJECTED', rejection_reason = #{reason} WHERE order_id = #{orderId}::uuid AND status = 'NEW'")
    int updateToRejected(@Param("orderId") String orderId, @Param("reason") String reason);
}
