package com.capstone.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityFilterChainMethodTest {

    @Test
    void securityFilterChain_buildsUsingProvidedHttpSecurity() throws Exception {
        SecurityConfig config = new SecurityConfig();
        HttpSecurity http = mock(HttpSecurity.class);
        AuthenticationEntryPoint entryPoint = config.authenticationEntryPoint();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter("sub");

        when(http.csrf(any())).thenReturn(http);
        when(http.sessionManagement(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.oauth2ResourceServer(any())).thenReturn(http);
        when(http.exceptionHandling(any())).thenReturn(http);
        SecurityFilterChain actual = config.securityFilterChain(http, entryPoint, converter);

        assertNull(actual);
        verify(http).csrf(any());
        verify(http).sessionManagement(any());
        verify(http).authorizeHttpRequests(any());
        verify(http).oauth2ResourceServer(any());
        verify(http).exceptionHandling(any());
        verify(http).build();
    }
}


