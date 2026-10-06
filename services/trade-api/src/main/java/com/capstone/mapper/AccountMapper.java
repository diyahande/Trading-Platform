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
            WHERE account_reference = #{accountReference}
            """)
    @Results(id = "accountMapByRef", value = {
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
    Optional<Account> findAccountByReference(
            @Param("accountReference") String accountReference
    );


    @Update("""
            UPDATE accounts
            SET cash_balance = #{cashBalance},
                version = version + 1
            WHERE account_id = #{accountId}
            AND version = #{version}
            """)
    int updateCashBalanceWithVersion(
            @Param("accountId") Long accountId,
            @Param("cashBalance") BigDecimal cashBalance,
            @Param("version") Long version
    );


    @Select("""
            SELECT 
                                a.account_reference AS account_reference,
                i.symbol,
                ph.quantity,
                ph.average_price AS average_cost
            FROM portfolio_holdings ph
                        JOIN portfolios p
                                ON ph.portfolio_id = p.portfolio_id
                        JOIN accounts a
                                ON p.account_id = a.account_id
            JOIN instruments i 
                ON ph.instrument_id = i.instrument_id
                        WHERE p.account_id = #{accountId}
            ORDER BY ph.last_updated DESC
            """)
    @Results(id = "positionMap", value = {

            @Result(property = "accountId",
                    column = "account_reference"),

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