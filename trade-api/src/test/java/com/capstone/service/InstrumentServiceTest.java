package com.capstone.service;

import com.capstone.entity.Instrument;
import com.capstone.enums.AssetClass;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.mapper.InstrumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock
    private InstrumentMapper instrumentMapper;

    private InstrumentService instrumentService;

    @BeforeEach
    void setUp() {
        instrumentService = new InstrumentService(instrumentMapper);
    }

    @Test
    void getInstrumentBySymbol_delegatesMapper() {
        Instrument i = instrument(false);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(Optional.of(i));

        Optional<Instrument> result = instrumentService.getInstrumentBySymbol("AAPL");

        assertTrue(result.isPresent());
    }

    @Test
    void getInstrumentById_delegatesMapper() {
        Instrument i = instrument(false);
        when(instrumentMapper.findById(1L)).thenReturn(Optional.of(i));

        Optional<Instrument> result = instrumentService.getInstrumentById(1L);

        assertTrue(result.isPresent());
    }

    @Test
    void validateAndGetInstrument_missing_throws() {
        when(instrumentMapper.findBySymbol("MISS")).thenReturn(Optional.empty());

        assertThrows(InstrumentNotFoundException.class, () -> instrumentService.validateAndGetInstrument("MISS"));
    }

    @Test
    void validateAndGetInstrument_delisted_throws() {
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(Optional.of(instrument(true)));

        assertThrows(InstrumentNotFoundException.class, () -> instrumentService.validateAndGetInstrument("AAPL"));
    }

    @Test
    void validateAndGetInstrument_tradable_returns() {
        Instrument i = instrument(false);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(Optional.of(i));

        Instrument result = instrumentService.validateAndGetInstrument("AAPL");

        assertEquals(i, result);
    }

    private static Instrument instrument(boolean delisted) {
        return new Instrument(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD", delisted,
                Instant.parse("2024-01-01T00:00:00Z"));
    }
}

