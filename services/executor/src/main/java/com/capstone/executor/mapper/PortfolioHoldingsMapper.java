package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;


public interface PortfolioHoldingsMapper {
    
    
    @Select("SELECT holding_id, portfolio_id, instrument_id, quantity, average_price, last_updated " +
            "FROM portfolio_holdings " +
            "WHERE portfolio_id = #{portfolioId} AND instrument_id = #{instrumentId}")
    PortfolioHoldingsDto findByPortfolioAndInstrument(
            @Param("portfolioId") Long portfolioId,
            @Param("instrumentId") Long instrumentId);
    
   
    @Update("UPDATE portfolio_holdings " +
            "SET quantity = #{newQuantity}, average_price = #{newAveragePrice}, " +
            "    last_updated = CURRENT_TIMESTAMP " +
            "WHERE portfolio_id = #{portfolioId} AND instrument_id = #{instrumentId}")
    int update(
            @Param("portfolioId") Long portfolioId,
            @Param("instrumentId") Long instrumentId,
            @Param("newQuantity") BigDecimal newQuantity,
            @Param("newAveragePrice") BigDecimal newAveragePrice);
 
    @Insert("INSERT INTO portfolio_holdings " +
            "(portfolio_id, instrument_id, quantity, average_price, last_updated) " +
            "VALUES (#{portfolioId}, #{instrumentId}, #{quantity}, #{averagePrice}, CURRENT_TIMESTAMP)")
    void insert(
            @Param("portfolioId") Long portfolioId,
            @Param("instrumentId") Long instrumentId,
            @Param("quantity") BigDecimal quantity,
            @Param("averagePrice") BigDecimal averagePrice);
}
