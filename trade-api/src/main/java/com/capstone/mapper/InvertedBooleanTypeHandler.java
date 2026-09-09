package com.capstone.mapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Custom type handler to map between database `is_tradable` column 
 * and Java entity `delisted` field (inverted boolean).
 */
public class InvertedBooleanTypeHandler extends BaseTypeHandler<Boolean> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType) throws SQLException {
        // Convert entity's delisted to database's is_tradable (inverted)
        ps.setBoolean(i, !parameter);
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
        boolean isTradable = rs.getBoolean(columnName);
        // Convert database's is_tradable to entity's delisted (inverted)
        return !isTradable;
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        boolean isTradable = rs.getBoolean(columnIndex);
        return !isTradable;
    }

    @Override
    public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        boolean isTradable = cs.getBoolean(columnIndex);
        return !isTradable;
    }
}
