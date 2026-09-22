package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Select;
import java.util.List;


public interface SymbolMapper {

  
    @Select({
        "SELECT DISTINCT i.symbol ",
        "FROM instruments i ",
        "WHERE i.is_tradable = TRUE ",
        "ORDER BY i.symbol"
    })
    List<String> fetchWatchedAndHeldSymbols();
}
