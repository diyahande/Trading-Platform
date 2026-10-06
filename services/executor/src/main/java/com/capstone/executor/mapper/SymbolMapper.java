package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Select;
import java.util.List;


public interface SymbolMapper {

  
    @Select({
        "SELECT DISTINCT i.symbol ",
        "FROM instruments i ",
        "WHERE i.is_tradable = TRUE ",
        "  AND (",
        "    EXISTS (SELECT 1 FROM watchlist w WHERE w.instrument_id = i.instrument_id) ",
        "    OR ",
        "    EXISTS (SELECT 1 FROM orders o WHERE o.instrument_id = i.instrument_id AND o.status = 'NEW') ",
        "  ) ",
        "ORDER BY i.symbol"
    })
    List<String> fetchWatchedAndHeldSymbols();
}
