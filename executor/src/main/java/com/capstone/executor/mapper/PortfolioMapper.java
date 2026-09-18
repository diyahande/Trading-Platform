package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for portfolios table.
 */
public interface PortfolioMapper {
    
    /**
     * Find portfolio by account_id.
     * Returns null if portfolio not found.
     */
    @Select("SELECT portfolio_id, account_id FROM portfolios WHERE account_id = #{accountId}")
    PortfolioDto findByAccountId(@Param("accountId") Long accountId);
}
