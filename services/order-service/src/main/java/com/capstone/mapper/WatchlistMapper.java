package com.capstone.mapper;

import com.capstone.entity.Watchlist;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WatchlistMapper {

    @Select("""
            SELECT 
                watchlist_id,
                user_id,
                instrument_id,
                name,
                created_at
            FROM watchlist
            WHERE user_id = #{userId}
            ORDER BY created_at DESC
            """)
    @Results(id = "watchlistMap", value = {
            @Result(property = "watchlistId", column = "watchlist_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "instrumentId", column = "instrument_id"),
            @Result(property = "name", column = "name"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<Watchlist> findByUserId(Long userId);

    @Select("""
            SELECT 
                watchlist_id,
                user_id,
                instrument_id,
                name,
                created_at
            FROM watchlist
            WHERE watchlist_id = #{watchlistId}
            """)
    @ResultMap("watchlistMap")
    Watchlist findById(Long watchlistId);

    @Insert("""
            INSERT INTO watchlist (user_id, instrument_id, name, created_at)
            VALUES (#{userId}, #{instrumentId}, #{name}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "watchlistId", keyColumn = "watchlist_id")
    void insert(Watchlist watchlist);

    @Delete("""
            DELETE FROM watchlist
            WHERE watchlist_id = #{watchlistId}
            """)
    void deleteById(Long watchlistId);

    @Delete("""
            DELETE FROM watchlist
            WHERE user_id = #{userId} AND instrument_id = #{instrumentId}
            """)
    void deleteByUserAndInstrument(Long userId, Long instrumentId);
}
