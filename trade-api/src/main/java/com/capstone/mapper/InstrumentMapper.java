package com.capstone.mapper;

import com.capstone.entity.Instrument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;

import java.util.Optional;

@Mapper
public interface InstrumentMapper {

    @Select("""
            SELECT 
                instrument_id,
                symbol,
                display_name,
                asset_class,
                quote_currency,
                is_tradable,
                created_at
            FROM instruments
            WHERE symbol = #{symbol}
            """)
    @Results(id = "instrumentMap", value = {
            @Result(property = "instrumentId", column = "instrument_id", id = true),
            @Result(property = "symbol", column = "symbol"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "assetClass", column = "asset_class"),
            @Result(property = "quoteCurrency", column = "quote_currency"),
            @Result(property = "delisted", column = "is_tradable",
                    typeHandler = com.capstone.mapper.InvertedBooleanTypeHandler.class),
            @Result(property = "createdAt", column = "created_at")
    })
    Optional<Instrument> findBySymbol(String symbol);

    @Select("""
            SELECT 
                instrument_id,
                symbol,
                display_name,
                asset_class,
                quote_currency,
                is_tradable,
                created_at
            FROM instruments
            WHERE instrument_id = #{instrumentId}
            """)
    @ResultMap("instrumentMap")
    Optional<Instrument> findById(Long instrumentId);
}
