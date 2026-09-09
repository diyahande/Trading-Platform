package com.capstone.mapper;

import com.capstone.entity.Order;
import com.capstone.enums.OrderStatus;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderMapper {

    @Insert("""
    INSERT INTO orders (
        account_id, account_reference, instrument_id, side, quantity,
        price, status, idempotency_key, created_at
    ) VALUES (
        #{accountId}, #{accountReference}, #{instrumentId},
        #{side}, #{quantity}, #{price}, #{status},
        #{idempotencyKey}, #{createdAt}
    )
    """)
        int insertOrder(Order order);

    @Select("""
            SELECT order_id, account_id, account_reference, instrument_id, side,
                   quantity, price, status, idempotency_key, created_at
            FROM orders
            WHERE order_id = #{orderId}
            """)
    @Results(id = "orderMap", value = {
        @Result(property = "orderId", column = "order_id", id = true),
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "accountReference", column = "account_reference"),
        @Result(property = "instrumentId", column = "instrument_id"),
        @Result(property = "side", column = "side"),
        @Result(property = "quantity", column = "quantity"),
        @Result(property = "price", column = "price"),
        @Result(property = "status", column = "status"),
        @Result(property = "idempotencyKey", column = "idempotency_key"),
        @Result(property = "createdAt", column = "created_at")
    })
    Optional<Order> findOrderById(@Param("orderId") Long orderId);

    @Select("""
            SELECT order_id, account_id, account_reference, instrument_id, side,
                   quantity, price, status, idempotency_key, created_at
            FROM orders
            WHERE account_id = #{accountId}
            ORDER BY created_at DESC
            """)
    @ResultMap("orderMap")
    List<Order> findOrdersByAccountId(@Param("accountId") Long accountId);

    @Select("""
            SELECT order_id, account_id, account_reference, instrument_id, side,
                   quantity, price, status, idempotency_key, created_at
            FROM orders
            WHERE idempotency_key = #{idempotencyKey}
            """)
    @ResultMap("orderMap")
    Optional<Order> findOrderByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE orders
            SET status = #{status}
            WHERE order_id = #{orderId}
            """)
    int updateOrderStatus(
            @Param("orderId") Long orderId,
            @Param("status") OrderStatus status
    );
}
