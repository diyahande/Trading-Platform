package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

/**
 * MyBatis mapper for portfolio_holdings table.
 * Handles position updates (new positions and modifications).
 * No version guard here - optimistic locking is only on the account version per ticket requirements.
 */
public interface PortfolioHoldingsMapper {
    
    /**
     * Find holding by portfolio_id and instrument_id.
     * Returns null if holding does not exist.
     */
    @Select("SELECT holding_id, portfolio_id, instrument_id, quantity, average_price, version, last_updated " +
            "FROM portfolio_holdings " +
            "WHERE portfolio_id = #{portfolioId} AND instrument_id = #{instrumentId}")
    PortfolioHoldingsDto findByPortfolioAndInstrument(
            @Param("portfolioId") Long portfolioId,
            @Param("instrumentId") Long instrumentId);
    
    /**
     * Update existing holding with new quantity and average price.
     * Simple update without version guard - optimistic locking is on accounts table only.
     * Returns 1 if successful, 0 if holding not found.
     */
    @Update("UPDATE portfolio_holdings " +
            "SET quantity = #{newQuantity}, average_price = #{newAveragePrice}, " +
            "    last_updated = CURRENT_TIMESTAMP " +
            "WHERE portfolio_id = #{portfolioId} AND instrument_id = #{instrumentId}")
    int update(
            @Param("portfolioId") Long portfolioId,
            @Param("instrumentId") Long instrumentId,
            @Param("newQuantity") BigDecimal newQuantity,
            @Param("newAveragePrice") BigDecimal newAveragePrice);
    
    /**
     * Insert new holding for an instrument in a portfolio.
     * Called when buying/selling an instrument for the first time in this portfolio.
     */
    @Insert("INSERT INTO portfolio_holdings " +
            "(portfolio_id, instrument_id, quantity, average_price, version, last_updated) " +
            "VALUES (#{portfolioId}, #{instrumentId}, #{quantity}, #{averagePrice}, 0, CURRENT_TIMESTAMP)")
    void insert(
            @Param("portfolioId") Long portfolioId,
            @Param("instrumentId") Long instrumentId,
            @Param("quantity") BigDecimal quantity,
            @Param("averagePrice") BigDecimal averagePrice);
}
