package com.capstone.mapper.typehandler;

import com.capstone.entity.PriceAlert;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class PriceAlertDirectionTypeHandler extends BaseTypeHandler<PriceAlert.Direction> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PriceAlert.Direction parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public PriceAlert.Direction getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public PriceAlert.Direction getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public PriceAlert.Direction getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private PriceAlert.Direction parse(String raw) throws SQLException {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return PriceAlert.Direction.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new SQLException("Invalid price_alert.direction value: '" + raw + "'", ex);
        }
    }
}
