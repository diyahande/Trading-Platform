package com.capstone.executor.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;


public class MarketDataQuotePayload {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("bid")
    private BigDecimal bid;

    @JsonProperty("ask")
    private BigDecimal ask;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("change")
    private BigDecimal change;

    @JsonProperty("changePercent")
    private BigDecimal changePercent;

    @JsonProperty("previousClose")
    private BigDecimal previousClose;

    @JsonProperty("marketState")
    private String marketState;

    @JsonProperty("stale")
    private boolean stale;

    @JsonProperty("quoteAsOf")
    private Instant quoteAsOf;

    public MarketDataQuotePayload() {}

    public MarketDataQuotePayload(
            String symbol,
            BigDecimal price,
            BigDecimal bid,
            BigDecimal ask,
            String currency,
            BigDecimal change,
            BigDecimal changePercent,
            BigDecimal previousClose,
            String marketState,
            boolean stale,
            Instant quoteAsOf) {
        this.symbol = symbol;
        this.price = price;
        this.bid = bid;
        this.ask = ask;
        this.currency = currency;
        this.change = change;
        this.changePercent = changePercent;
        this.previousClose = previousClose;
        this.marketState = marketState;
        this.stale = stale;
        this.quoteAsOf = quoteAsOf;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getChange() {
        return change;
    }

    public void setChange(BigDecimal change) {
        this.change = change;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public String getMarketState() {
        return marketState;
    }

    public void setMarketState(String marketState) {
        this.marketState = marketState;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public Instant getQuoteAsOf() {
        return quoteAsOf;
    }

    public void setQuoteAsOf(Instant quoteAsOf) {
        this.quoteAsOf = quoteAsOf;
    }
}
