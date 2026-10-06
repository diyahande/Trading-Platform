package com.capstone.executor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// MyBatis mapper for instruments table
public interface InstrumentMapper {
    
    // Find instrument by symbol; check current tradable flag for execution-time re-check
    @Select("SELECT instrument_id, symbol, display_name, asset_class, quote_currency, is_tradable AS tradable, created_at FROM instruments WHERE symbol = #{symbol}")
    InstrumentDto findBySymbol(@Param("symbol") String symbol);
}
