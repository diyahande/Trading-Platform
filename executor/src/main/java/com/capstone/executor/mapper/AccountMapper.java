package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;


public interface AccountMapper {
    
   
    @Select("SELECT account_id, user_id, account_reference, holder_name, currency, " +
            "cash_balance, status, version, created_at " +
            "FROM accounts WHERE account_id = #{accountId}")
    AccountDto findById(@Param("accountId") Long accountId);
    
 
    @Update("UPDATE accounts " +
            "SET cash_balance = cash_balance + #{cashDelta}, " +
            "    version = version + 1 " +
            "WHERE account_id = #{accountId} AND version = #{currentVersion} " +
            "  AND status = 'ACTIVE'")
    int updateCashWithVersionGuard(
            @Param("accountId") Long accountId,
            @Param("cashDelta") BigDecimal cashDelta,
            @Param("currentVersion") Long currentVersion);
}
