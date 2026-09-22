package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


public interface PortfolioMapper {
    
 
    @Select("SELECT portfolio_id, account_id FROM portfolios WHERE account_id = #{accountId}")
    PortfolioDto findByAccountId(@Param("accountId") Long accountId);
}
