package com.capstone.config;

import com.capstone.security.OrderedJwtDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void jwtDecoder_returnsOrderedDecoder() {
        assertInstanceOf(OrderedJwtDecoder.class,
                securityConfig.jwtDecoder("test-secret-key-for-jwt-signing-min-32-bytes"));
    }

    @Test
    void jwtAuthenticationConverter_usesConfiguredPrincipalClaim() {
        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter("account_ref");
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "sub-value", "account_ref", "acc-123")
        );

        Authentication authentication = converter.convert(jwt);

        assertNotNull(authentication);
        assertEquals("acc-123", authentication.getName());
    }

    @Test
    void authenticationEntryPoint_writes401JsonResponse() throws Exception {
        AuthenticationEntryPoint entryPoint = securityConfig.authenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new AuthenticationException("bad") { });

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorized\"}", response.getContentAsString());
    }
}

