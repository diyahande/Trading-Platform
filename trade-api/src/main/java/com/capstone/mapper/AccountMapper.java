package com.capstone.mapper;

import com.capstone.dto.PositionResponse;
import com.capstone.entity.Account;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AccountMapper {

    @Select("""
            SELECT 
                account_id,
                user_id,
                account_reference,
                holder_name,
                currency,
                cash_balance,
                status,
                version,
                created_at
            FROM accounts
            WHERE account_id = #{accountId}
            """)
    @Results(id = "accountMap", value = {
            @Result(property = "accountId",
                    column = "account_id",
                    id = true),

            @Result(property = "userId",
                    column = "user_id"),

            @Result(property = "accountReference",
                    column = "account_reference"),

            @Result(property = "holderName",
                    column = "holder_name"),

            @Result(property = "currency",
                    column = "currency"),

            @Result(property = "cashBalance",
                    column = "cash_balance"),

            @Result(property = "status",
                    column = "status",
                    typeHandler = org.apache.ibatis.type.EnumTypeHandler.class),

            @Result(property = "version",
                    column = "version"),

            @Result(property = "createdAt",
                    column = "created_at")
    })
    Optional<Account> findAccountById(
            @Param("accountId") Long accountId
    );


    @Update("""
            UPDATE accounts
            SET cash_balance = #{cashBalance}
            WHERE account_id = #{accountId}
            """)
    int updateCashBalance(
            @Param("accountId") Long accountId,
            @Param("cashBalance") BigDecimal cashBalance
    );


    @Select("""
            SELECT 
                #{accountId} AS account_id,
                i.symbol,
                ph.quantity,
                ph.average_price AS average_cost
            FROM portfolio_holdings ph
            JOIN instruments i 
                ON ph.instrument_id = i.instrument_id
            WHERE ph.portfolio_id = (
                SELECT portfolio_id 
                FROM portfolios 
                WHERE account_id = #{accountId}
            )
            ORDER BY ph.last_updated DESC
            """)
    @Results(id = "positionMap", value = {

            @Result(property = "accountId",
                    column = "account_id"),

            @Result(property = "symbol",
                    column = "symbol"),

            @Result(property = "quantity",
                    column = "quantity"),

            @Result(property = "averageCost",
                    column = "average_cost")
    })
    List<PositionResponse> findPositionsByAccountId(
            @Param("accountId") Long accountId
    );
}