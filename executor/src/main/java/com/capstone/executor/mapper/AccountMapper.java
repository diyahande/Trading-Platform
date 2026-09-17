package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// MyBatis mapper for accounts table
public interface AccountMapper {
    
    // Find account by ID; check current status and cash balance for execution-time re-check
    @Select("SELECT account_id, user_id, account_reference, holder_name, currency, cash_balance, status, version, created_at FROM accounts WHERE account_id = #{accountId}")
    AccountDto findById(@Param("accountId") Long accountId);
}
