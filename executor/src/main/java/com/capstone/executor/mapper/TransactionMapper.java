package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import java.math.BigDecimal;

// MyBatis mapper for transactions table
public interface TransactionMapper {
    
    // Insert transaction record when order fills
    // Returns void; database auto-generates transaction_id
    @Insert("INSERT INTO transactions (order_id, instrument_id, quantity, price, status) VALUES (#{orderId}::uuid, #{instrumentId}, #{quantity}, #{price}, #{status})")
    void insert(@Param("orderId") String orderId, 
                @Param("instrumentId") Long instrumentId, 
                @Param("quantity") BigDecimal quantity, 
                @Param("price") BigDecimal price, 
                @Param("status") String status);
}
