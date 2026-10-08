package com.capstone.entity;

import com.capstone.enums.AssetClass;

import java.time.Instant;

public class Instrument {

    private final Long instrumentId;
    private final String symbol;
    private final String displayName;
    private final AssetClass assetClass;
    private final String quoteCurrency;

    private boolean delisted;

    private final Instant createdAt;

    public Instrument(
            Long instrumentId,
            String symbol,
            String displayName,
            AssetClass assetClass,
            String quoteCurrency,
            boolean delisted,
            Instant createdAt) {

        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.displayName = displayName;
        this.assetClass = assetClass;
        this.quoteCurrency = quoteCurrency;
        this.delisted = delisted;
        this.createdAt = createdAt;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public boolean isDelisted() {
        return delisted;
    }

    public boolean isTradable() {
        return !delisted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void delist() {
        this.delisted = true;
    }
}