package com.capstone.entity;

import java.time.Instant;

public class CustomerPreference {

    private final Long preferenceId;
    private final String accountReference;
    private String theme;
    private String currency;
    private Boolean emailNotificationsEnabled;
    private final Instant createdAt;
    private Instant updatedAt;

    public CustomerPreference(
            Long preferenceId,
            String accountReference,
            String theme,
            String currency,
            Boolean emailNotificationsEnabled,
            Instant createdAt,
            Instant updatedAt) {
        this.preferenceId = preferenceId;
        this.accountReference = accountReference;
        this.theme = theme;
        this.currency = currency;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getPreferenceId() { return preferenceId; }
    public String getAccountReference() { return accountReference; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Boolean getEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled) { this.emailNotificationsEnabled = emailNotificationsEnabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
