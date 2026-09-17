package com.capstone.executor.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FauxnanceQuoteResponse {

    @JsonProperty("data")
    private Quote data;

    public FauxnanceQuoteResponse() {
    }

    public Quote getData() {
        return data;
    }

    public void setData(Quote data) {
        this.data = data;
    }
}