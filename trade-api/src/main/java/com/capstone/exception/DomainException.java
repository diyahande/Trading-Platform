package com.capstone.exception;
public abstract class DomainException extends RuntimeException {

    private final String catalogueCode;

    protected DomainException(String catalogueCode, String catalogueMessage) {
        super(catalogueMessage);
        this.catalogueCode = catalogueCode;
    }

    public String getCatalogueCode() {
        return catalogueCode;
    }
}
