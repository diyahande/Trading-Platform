package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


public interface PortfolioMapper {
    
 
    @Select("SELECT portfolio_id, account_id FROM portfolios WHERE account_id = #{accountId}")
    PortfolioDto findByAccountId(@Param("accountId") Long accountId);

    @Insert("INSERT INTO portfolios (account_id, current_value, invested_value, updated_at) VALUES (#{accountId}, 0, 0, CURRENT_TIMESTAMP)")
    void insert(@Param("accountId") Long accountId);
}
