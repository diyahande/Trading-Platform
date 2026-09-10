package com.capstone.service;

import com.capstone.entity.Instrument;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.mapper.InstrumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * InstrumentService handles instrument-related business logic.
 * Provides symbol-to-ID lookup and tradability validation.
 */
@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentMapper instrumentMapper;

    /**
     * Get instrument by symbol.
     * Returns empty Optional if instrument doesn't exist.
     * 
     * @param symbol Instrument symbol (e.g., "INFY", "AAPL")
     * @return Instrument if found, empty Optional otherwise
     */
    public Optional<Instrument> getInstrumentBySymbol(String symbol) {
        return instrumentMapper.findBySymbol(symbol);
    }

    /**
     * Get instrument by ID.
     * Returns empty Optional if instrument doesn't exist.
     * 
     * @param instrumentId Instrument ID
     * @return Instrument if found, empty Optional otherwise
     */
    public Optional<Instrument> getInstrumentById(Long instrumentId) {
        return instrumentMapper.findById(instrumentId);
    }

    /**
     * Validate that an instrument exists and is tradable.
     * Throws IllegalArgumentException with INS-404 if:
     * - Instrument doesn't exist
     * - Instrument is delisted (not tradable)
     * 
     * @param symbol Instrument symbol
     * @return Instrument if valid
     * @throws IllegalArgumentException if instrument doesn't exist or is not tradable
     */
    public Instrument validateAndGetInstrument(String symbol) {
        Optional<Instrument> instrumentOpt = getInstrumentBySymbol(symbol);

        if (instrumentOpt.isEmpty()) {
            throw new InstrumentNotFoundException(symbol);
        }

        Instrument instrument = instrumentOpt.get();

        if (!instrument.isTradable()) {
            throw new InstrumentNotFoundException(symbol);
        }

        return instrument;
    }
}
