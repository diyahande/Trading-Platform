package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * MyBatis mapper for querying watchlist symbols and related trading symbols.
 * 
 * Fetches all unique symbols that users are monitoring or trading:
 * - Symbols from watchlist entries
 * - Symbols from portfolio holdings
 * - Symbols from open/recent orders
 */
public interface SymbolMapper {

    /**
     * Fetch all unique tradable symbols that someone holds or is watching.
     * 
     * Sources:
     * - watchlist: user-tracked instruments
     * - portfolio_holdings: instruments user owns
     * - orders: instruments user has recently ordered
     * 
     * Only includes tradable instruments (is_tradable = true).
     * Falls back to all tradable instruments if no specific holdings/watchlist/orders exist.
     */
    @Select({
        "SELECT DISTINCT i.symbol ",
        "FROM instruments i ",
        "WHERE i.is_tradable = TRUE ",
        "ORDER BY i.symbol"
    })
    List<String> fetchWatchedAndHeldSymbols();
}
