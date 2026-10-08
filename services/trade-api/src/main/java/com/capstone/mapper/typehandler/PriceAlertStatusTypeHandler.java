package com.capstone.mapper.typehandler;

import com.capstone.entity.PriceAlert;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class PriceAlertStatusTypeHandler extends BaseTypeHandler<PriceAlert.Status> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PriceAlert.Status parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public PriceAlert.Status getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public PriceAlert.Status getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public PriceAlert.Status getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private PriceAlert.Status parse(String raw) throws SQLException {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return PriceAlert.Status.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new SQLException("Invalid price_alert.status value: '" + raw + "'", ex);
        }
    }
}
