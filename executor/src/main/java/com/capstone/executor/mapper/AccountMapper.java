package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

/**
 * MyBatis mapper for accounts table.
 * Supports both read-only queries and version-guarded cash updates (optimistic locking).
 */
public interface AccountMapper {
    
    /**
     * Find account by ID; check current status and cash balance for execution-time re-check.
     */
    @Select("SELECT account_id, user_id, account_reference, holder_name, currency, " +
            "cash_balance, status, version, created_at " +
            "FROM accounts WHERE account_id = #{accountId}")
    AccountDto findById(@Param("accountId") Long accountId);
    
    /**
     * Update account cash balance with version guard (optimistic lock).
     * Only succeeds if version matches current row version.
     * Atomically increments version on success.
     * Returns 1 if successful, 0 if version mismatch (concurrent modification by another service).
     * 
     * Used during order settlement to move cash between account and instrument positions.
     * Zero rows affected means another delivery or concurrent update already modified the account.
     */
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
