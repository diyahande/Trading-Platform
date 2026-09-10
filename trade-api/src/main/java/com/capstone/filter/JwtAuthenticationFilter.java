package com.capstone.filter;

import com.capstone.dto.ErrorResponse;
import com.capstone.exception.AuthenticationException;
import com.capstone.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter for all /api/* routes.
 * Validates token before any controller runs.
 * Stores validated account key in request attribute for controllers to access.
 * 
 * Note: Registered as a bean via ApplicationConfig.jwtAuthenticationFilter()
 * to enable proper filter ordering and URL pattern configuration.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    private static final String API_V1_PREFIX = "/api/v1";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCOUNT_KEY_ATTRIBUTE = "accountKey";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Validate every route under /api/v1/** before any controller runs.
        if (!isProtectedRoute(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractTokenFromHeader(request);
            if (token == null) {
                throw new AuthenticationException("Missing authorization header");
            }

            // Validate token once (signature, expiry, algorithm) and then read subject.
            String accountKey = tokenProvider.validateAndGetSubject(token);
            request.setAttribute(ACCOUNT_KEY_ATTRIBUTE, accountKey);

            filterChain.doFilter(request, response);
        } catch (AuthenticationException e) {
            sendAuthError(response);
        }
    }

    private boolean isProtectedRoute(String requestUri) {
        return API_V1_PREFIX.equals(requestUri) || requestUri.startsWith(API_V1_PREFIX + "/");
    }

    /**
     * Extract Bearer token from Authorization header.
     * Returns null if header missing or scheme is wrong.
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    /**
     * Send uniform AUTH-401 response for all authentication failures.
     */
    private void sendAuthError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse error = new ErrorResponse();
        error.setErrorCode("AUTH-401");
        error.setMessage("Unauthorized");

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    /**
     * Get the validated account key from request attribute.
     */
    public static String getAccountKeyFromRequest(HttpServletRequest request) {
        return (String) request.getAttribute(ACCOUNT_KEY_ATTRIBUTE);
    }
}
