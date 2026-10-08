package com.capstone.service;

import com.capstone.dto.CustomerPreferenceResponse;
import com.capstone.dto.UpdatePreferenceRequest;
import com.capstone.entity.Account;
import com.capstone.entity.CustomerPreference;
import com.capstone.mapper.CustomerPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerPreferenceService {

    private static final String DEFAULT_THEME = "LIGHT";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final boolean DEFAULT_EMAIL_NOTIFICATIONS = true;

    private static final Set<String> VALID_THEMES = Set.of("LIGHT", "DARK");
    private static final Set<String> VALID_CURRENCIES = Set.of("USD", "INR");

    private final CustomerPreferenceMapper customerPreferenceMapper;

    public CustomerPreferenceResponse getPreferences(String accountReference) {
        Optional<CustomerPreference> pref = customerPreferenceMapper.findByAccountReference(accountReference);
        if (pref.isEmpty()) {
            return new CustomerPreferenceResponse(DEFAULT_THEME, DEFAULT_CURRENCY, DEFAULT_EMAIL_NOTIFICATIONS);
        }
        CustomerPreference p = pref.get();
        return new CustomerPreferenceResponse(
                p.getTheme(),
                p.getCurrency(),
                p.getEmailNotificationsEnabled() != null ? p.getEmailNotificationsEnabled() : DEFAULT_EMAIL_NOTIFICATIONS
        );
    }

    public CustomerPreferenceResponse updatePreferences(String accountReference, UpdatePreferenceRequest request) {
        // Fetch existing to preserve non-updated fields
        Optional<CustomerPreference> existing = customerPreferenceMapper.findByAccountReference(accountReference);

        String theme = resolveTheme(request.getTheme(), existing);
        String currency = resolveCurrency(request.getCurrency(), existing);
        boolean emailNotificationsEnabled = resolveEmailNotifications(request.getEmailNotificationsEnabled(), existing);

        customerPreferenceMapper.upsertPreference(
                accountReference,
                theme,
                currency,
                emailNotificationsEnabled
        );

        return new CustomerPreferenceResponse(theme, currency, emailNotificationsEnabled);
    }

    public boolean isEmailNotificationsEnabled(String accountReference) {
        return customerPreferenceMapper
                .findEmailNotificationsEnabledByAccountReference(accountReference)
                .orElse(true); // Default: notifications ON if no preference set
    }

    private String resolveTheme(String requested, Optional<CustomerPreference> existing) {
        if (requested != null && VALID_THEMES.contains(requested.toUpperCase())) {
            return requested.toUpperCase();
        }
        return existing.map(CustomerPreference::getTheme).orElse(DEFAULT_THEME);
    }

    private String resolveCurrency(String requested, Optional<CustomerPreference> existing) {
        if (requested != null && VALID_CURRENCIES.contains(requested.toUpperCase())) {
            return requested.toUpperCase();
        }
        return existing.map(CustomerPreference::getCurrency).orElse(DEFAULT_CURRENCY);
    }

    private boolean resolveEmailNotifications(Boolean requested, Optional<CustomerPreference> existing) {
        if (requested != null) {
            return requested;
        }
        return existing.map(p -> p.getEmailNotificationsEnabled() != null ? p.getEmailNotificationsEnabled() : DEFAULT_EMAIL_NOTIFICATIONS)
                .orElse(DEFAULT_EMAIL_NOTIFICATIONS);
    }
}
