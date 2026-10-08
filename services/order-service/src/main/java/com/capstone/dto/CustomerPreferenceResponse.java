package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPreferenceResponse {
    private String theme;
    private String currency;
    private Boolean emailNotificationsEnabled;
}
