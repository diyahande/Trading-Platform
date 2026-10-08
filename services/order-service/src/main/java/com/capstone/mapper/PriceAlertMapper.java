package com.capstone.mapper;

import com.capstone.entity.PriceAlert;
import com.capstone.mapper.typehandler.PriceAlertDirectionTypeHandler;
import com.capstone.mapper.typehandler.PriceAlertStatusTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PriceAlertMapper {

    @Select("""
            SELECT 
                alert_id,
                watchlist_id,
                threshold,
                                UPPER(TRIM(direction)) AS direction,
                                UPPER(TRIM(status)) AS status,
                created_at,
                triggered_at,
                updated_at
            FROM price_alert
            WHERE watchlist_id = #{watchlistId}
            ORDER BY created_at DESC
            """)
    @ConstructorArgs(value = {
            @Arg(column = "alert_id", javaType = Long.class, id = true),
            @Arg(column = "watchlist_id", javaType = Long.class),
            @Arg(column = "threshold", javaType = java.math.BigDecimal.class),
            @Arg(column = "direction", javaType = PriceAlert.Direction.class,
                    typeHandler = PriceAlertDirectionTypeHandler.class),
            @Arg(column = "status", javaType = PriceAlert.Status.class,
                    typeHandler = PriceAlertStatusTypeHandler.class),
            @Arg(column = "created_at", javaType = java.time.Instant.class),
            @Arg(column = "triggered_at", javaType = java.time.Instant.class),
            @Arg(column = "updated_at", javaType = java.time.Instant.class)
    })
    List<PriceAlert> findByWatchlistId(Long watchlistId);

    @Select("""
            SELECT 
                alert_id,
                watchlist_id,
                threshold,
                                UPPER(TRIM(direction)) AS direction,
                                UPPER(TRIM(status)) AS status,
                created_at,
                triggered_at,
                updated_at
            FROM price_alert
            WHERE alert_id = #{alertId}
            """)
    @ConstructorArgs(value = {
            @Arg(column = "alert_id", javaType = Long.class, id = true),
            @Arg(column = "watchlist_id", javaType = Long.class),
            @Arg(column = "threshold", javaType = java.math.BigDecimal.class),
            @Arg(column = "direction", javaType = PriceAlert.Direction.class,
                    typeHandler = PriceAlertDirectionTypeHandler.class),
            @Arg(column = "status", javaType = PriceAlert.Status.class,
                    typeHandler = PriceAlertStatusTypeHandler.class),
            @Arg(column = "created_at", javaType = java.time.Instant.class),
            @Arg(column = "triggered_at", javaType = java.time.Instant.class),
            @Arg(column = "updated_at", javaType = java.time.Instant.class)
    })
    Optional<PriceAlert> findById(Long alertId);

    /**
     * Find all active alerts for a given symbol across all users.
     * Used by market data consumer to evaluate alerts per symbol.
     * Query joins: price_alert -> watchlist -> instruments
     */
    @Select("""
            SELECT 
                pa.alert_id,
                pa.watchlist_id,
                pa.threshold,
                                UPPER(TRIM(pa.direction)) AS direction,
                                UPPER(TRIM(pa.status)) AS status,
                pa.created_at,
                pa.triggered_at,
                pa.updated_at
            FROM price_alert pa
            INNER JOIN watchlist w ON pa.watchlist_id = w.watchlist_id
            INNER JOIN instruments i ON w.instrument_id = i.instrument_id
            WHERE i.symbol = #{symbol} AND pa.status = 'ACTIVE'
            """)
    @ConstructorArgs(value = {
            @Arg(column = "alert_id", javaType = Long.class, id = true),
            @Arg(column = "watchlist_id", javaType = Long.class),
            @Arg(column = "threshold", javaType = java.math.BigDecimal.class),
            @Arg(column = "direction", javaType = PriceAlert.Direction.class,
                    typeHandler = PriceAlertDirectionTypeHandler.class),
            @Arg(column = "status", javaType = PriceAlert.Status.class,
                    typeHandler = PriceAlertStatusTypeHandler.class),
            @Arg(column = "created_at", javaType = java.time.Instant.class),
            @Arg(column = "triggered_at", javaType = java.time.Instant.class),
            @Arg(column = "updated_at", javaType = java.time.Instant.class)
    })
    List<PriceAlert> findActiveBySymbol(String symbol);

    @Insert("""
            INSERT INTO price_alert (watchlist_id, threshold, direction, status, created_at, updated_at)
            VALUES (#{watchlistId}, #{threshold}, #{direction}, #{status}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "alertId", keyColumn = "alert_id")
    void insert(PriceAlert alert);

    @Update("""
            UPDATE price_alert
            SET status = #{status}, triggered_at = #{triggeredAt}, updated_at = #{updatedAt}
            WHERE alert_id = #{alertId}
            """)
    void updateStatus(PriceAlert alert);

    @Delete("""
            DELETE FROM price_alert
            WHERE alert_id = #{alertId}
            """)
    void deleteById(Long alertId);

    /**
     * Count active alerts for an account (for cap enforcement).
     * Joins: price_alert -> watchlist -> users
     */
    @Select("""
            SELECT COUNT(*)
            FROM price_alert pa
            INNER JOIN watchlist w ON pa.watchlist_id = w.watchlist_id
            WHERE w.user_id = #{userId} AND pa.status = 'ACTIVE'
            """)
    int countActiveByUserId(Long userId);
}
