package com.capstone.mapper;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TypeHandlersTest {

    @Test
    void uuidTypeHandler_setsAndReadsValues() throws Exception {
        UuidTypeHandler handler = new UuidTypeHandler();
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        CallableStatement cs = mock(CallableStatement.class);

        UUID uuid = UUID.randomUUID();
        handler.setNonNullParameter(ps, 1, uuid, JdbcType.OTHER);
        verify(ps).setObject(1, uuid);

        when(rs.getObject("col")).thenReturn(uuid);
        assertEquals(uuid, handler.getNullableResult(rs, "col"));

        when(rs.getObject(2)).thenReturn(uuid.toString());
        assertEquals(uuid, handler.getNullableResult(rs, 2));

        when(cs.getObject(3)).thenReturn(null);
        assertNull(handler.getNullableResult(cs, 3));
    }

    @Test
    void invertedBooleanTypeHandler_invertsBothWays() throws Exception {
        InvertedBooleanTypeHandler handler = new InvertedBooleanTypeHandler();
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        CallableStatement cs = mock(CallableStatement.class);

        handler.setNonNullParameter(ps, 1, true, JdbcType.BOOLEAN);
        verify(ps).setBoolean(1, false);

        when(rs.getBoolean("tradable")).thenReturn(true);
        assertFalse(handler.getNullableResult(rs, "tradable"));

        when(rs.getBoolean(2)).thenReturn(false);
        assertTrue(handler.getNullableResult(rs, 2));

        when(cs.getBoolean(3)).thenReturn(true);
        assertFalse(handler.getNullableResult(cs, 3));
    }
}

