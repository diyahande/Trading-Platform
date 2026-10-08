package com.capstone.mapper;

import com.capstone.dto.FilledOrderRow;
import com.capstone.dto.PortfolioHoldingRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PortfolioMapper {

    @Select("""
            SELECT
                p.account_id AS account_id,
                i.symbol AS symbol,
                CAST(ph.quantity AS INTEGER) AS quantity,
                ph.average_price AS average_cost,
                i.quote_currency AS currency
            FROM portfolios p
            JOIN portfolio_holdings ph ON ph.portfolio_id = p.portfolio_id
            JOIN instruments i ON i.instrument_id = ph.instrument_id
            WHERE p.account_id = #{accountId}
              AND ph.quantity > 0
            ORDER BY i.symbol ASC
            """)
    @Results(id = "portfolioHoldingRowMap", value = {
            @Result(property = "accountId", column = "account_id"),
            @Result(property = "symbol", column = "symbol"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "averageCost", column = "average_cost"),
            @Result(property = "currency", column = "currency")
    })
    List<PortfolioHoldingRow> findOpenHoldingsByAccountId(@Param("accountId") Long accountId);

    @Select("""
            SELECT
                i.symbol AS symbol,
                o.side AS side,
                CAST(o.quantity AS INTEGER) AS quantity,
                COALESCE(o.executed_price, o.price) AS executed_price,
                COALESCE(o.executed_on, o.created_at) AS executed_on
            FROM orders o
            JOIN instruments i ON i.instrument_id = o.instrument_id
            WHERE o.account_id = #{accountId}
              AND o.status = 'FILLED'
            ORDER BY COALESCE(o.executed_on, o.created_at) ASC, o.created_at ASC
            """)
    @Results(id = "filledOrderRowMap", value = {
            @Result(property = "symbol", column = "symbol"),
            @Result(property = "side", column = "side"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "executedPrice", column = "executed_price"),
            @Result(property = "executedOn", column = "executed_on")
    })
    List<FilledOrderRow> findFilledOrdersForAccount(@Param("accountId") Long accountId);
}
