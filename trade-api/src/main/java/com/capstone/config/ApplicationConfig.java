package com.capstone.config;

import com.capstone.filter.JwtAuthenticationFilter;
import com.capstone.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application configuration for security filters and beans.
 */
@Configuration
public class ApplicationConfig {

    /**
     * Configure ObjectMapper bean for JSON serialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Register JWT authentication filter to run before all servlet filters.
     * Handles all /api/* routes.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            ObjectMapper objectMapper) {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, objectMapper);
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
