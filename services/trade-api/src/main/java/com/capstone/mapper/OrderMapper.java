package com.capstone.mapper;

import com.capstone.dto.OrderHistoryEntry;
import com.capstone.entity.Order;
import com.capstone.enums.OrderStatus;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface OrderMapper {

    @Insert("""
    INSERT INTO orders (

        order_id, account_id, account_reference, instrument_id, side, quantity,
        price, status, idempotency_key, created_at
    ) VALUES (
                #{orderId, jdbcType=OTHER, typeHandler=com.capstone.mapper.UuidTypeHandler}, #{accountId}, #{accountReference}, #{instrumentId},
        #{side}, #{quantity}, #{price}, #{status},
        #{idempotencyKey}, #{createdAt}
    )
    """)
        int insertOrder(Order order);

    @Select("""
            SELECT order_id, account_id, account_reference, instrument_id, side,
                   quantity, price, status, idempotency_key, created_at
            FROM orders
                        WHERE order_id = #{orderId, jdbcType=OTHER, typeHandler=com.capstone.mapper.UuidTypeHandler}
            """)
    @Results(id = "orderMap", value = {
                @Result(property = "orderId", column = "order_id", id = true,
                                javaType = UUID.class, jdbcType = JdbcType.OTHER, typeHandler = UuidTypeHandler.class),
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
        Optional<Order> findOrderById(@Param("orderId") UUID orderId);

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
            SELECT
                CONCAT('ORD-', o.order_id) AS order_id,
                o.account_reference AS account_id,
                i.symbol AS symbol,
                o.side AS side,
                CAST(o.quantity AS INTEGER) AS quantity,
                o.price AS price,
                o.executed_price AS executed_price,
                o.status AS status,
                o.idempotency_key AS idempotency_key,
                o.created_at AS created_on
            FROM orders o
            LEFT JOIN instruments i
              ON i.instrument_id = o.instrument_id
            WHERE o.account_id = #{accountId}
            ORDER BY o.created_at DESC
            """)
    @Results(id = "orderHistoryMap", value = {
            @Result(property = "orderId", column = "order_id"),
            @Result(property = "accountId", column = "account_id"),
            @Result(property = "symbol", column = "symbol"),
            @Result(property = "side", column = "side"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "price", column = "price"),
            @Result(property = "executedPrice", column = "executed_price"),
            @Result(property = "status", column = "status"),
            @Result(property = "idempotencyKey", column = "idempotency_key"),
            @Result(property = "createdOn", column = "created_on")
    })
    List<OrderHistoryEntry> findOrderHistoryByAccountId(@Param("accountId") Long accountId);

    @Select("""
            SELECT order_id, account_id, account_reference, instrument_id, side,
                   quantity, price, status, idempotency_key, created_at
            FROM orders
            WHERE idempotency_key = #{idempotencyKey}
            """)
    @ResultMap("orderMap")
    Optional<Order> findOrderByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT ph.quantity
            FROM portfolios p
            JOIN portfolio_holdings ph
              ON ph.portfolio_id = p.portfolio_id
            WHERE p.account_id = #{accountId}
              AND ph.instrument_id = #{instrumentId}
            FOR UPDATE OF ph
            """)
    Optional<BigDecimal> findHoldingQuantityForUpdate(
            @Param("accountId") Long accountId,
            @Param("instrumentId") Long instrumentId
    );

    @Update("""
            UPDATE orders
            SET status = #{newStatus}
            WHERE order_id = #{orderId, jdbcType=OTHER, typeHandler=com.capstone.mapper.UuidTypeHandler}
            AND status = #{expectedStatus}
            """)
    int updateOrderStatusConditional(
            @Param("orderId") UUID orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("newStatus") OrderStatus newStatus
    );

    @Update("""
            UPDATE orders
            SET status = #{status}
            WHERE order_id = #{orderId, jdbcType=OTHER, typeHandler=com.capstone.mapper.UuidTypeHandler}
            """)
    int updateOrderStatus(
            @Param("orderId") UUID orderId,
            @Param("status") OrderStatus status
    );
}
