package com.capstone.service;

import com.capstone.entity.Instrument;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.mapper.InstrumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentMapper instrumentMapper;

    
    public Optional<Instrument> getInstrumentBySymbol(String symbol) {
        return instrumentMapper.findBySymbol(symbol);
    }


    public Optional<Instrument> getInstrumentById(Long instrumentId) {
        return instrumentMapper.findById(instrumentId);
    }

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
