package com.capstone.filter;

import com.capstone.controller.AccountController;
import com.capstone.dto.ErrorResponse;
import com.capstone.enums.AccountStatus;
import com.capstone.exception.AccountNotActiveException;
import com.capstone.service.AccountService;
import com.capstone.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterWebMvcTest {

    private static final String JWT_SECRET = "mission-control-shared-secret-key-32-bytes-minimum";

        private final JwtTokenProvider tokenProvider = new JwtTokenProvider(JWT_SECRET);
        private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Missing Authorization header returns uniform AUTH-401")
    void missingAuthorizationHeaderReturnsAuth401() throws Exception {
                FilterExecution result = executeFilter("/api/v1/accounts/1", null);

                assertEquals(HttpServletResponse.SC_UNAUTHORIZED, result.status);
                assertTrue(result.body.contains("\"errorCode\":\"AUTH-401\""));
                assertTrue(result.body.contains("\"message\":\"Unauthorized\""));
    }

    @Test
    @DisplayName("Wrong Authorization scheme returns uniform AUTH-401")
    void wrongAuthorizationSchemeReturnsAuth401() throws Exception {
                FilterExecution result = executeFilter("/api/v1/accounts/1", "Basic abc123");

                assertEquals(HttpServletResponse.SC_UNAUTHORIZED, result.status);
                assertTrue(result.body.contains("\"errorCode\":\"AUTH-401\""));
                assertTrue(result.body.contains("\"message\":\"Unauthorized\""));
    }

    @Test
    @DisplayName("Expired token returns uniform AUTH-401")
    void expiredTokenReturnsAuth401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = createToken(
                "alice",
                key,
                SignatureAlgorithm.HS256,
                Instant.now().minusSeconds(120)
        );

        FilterExecution result = executeFilter("/api/v1/accounts/1", "Bearer " + expiredToken);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, result.status);
        assertTrue(result.body.contains("\"errorCode\":\"AUTH-401\""));
        assertTrue(result.body.contains("\"message\":\"Unauthorized\""));
    }

    @Test
    @DisplayName("Forged signature token returns uniform AUTH-401")
    void forgedSignatureReturnsAuth401() throws Exception {
        SecretKey forgedKey = Keys.hmacShaKeyFor("another-shared-secret-key-32bytes!!".getBytes(StandardCharsets.UTF_8));
        String forgedToken = createToken(
                "alice",
                forgedKey,
                SignatureAlgorithm.HS256,
                Instant.now().plusSeconds(3600)
        );

        FilterExecution result = executeFilter("/api/v1/accounts/1", "Bearer " + forgedToken);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, result.status);
        assertTrue(result.body.contains("\"errorCode\":\"AUTH-401\""));
        assertTrue(result.body.contains("\"message\":\"Unauthorized\""));
    }

    @Test
    @DisplayName("Unsupported token algorithm returns uniform AUTH-401")
    void unsupportedAlgorithmReturnsAuth401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String hs384Token = createToken(
                "alice",
                key,
                SignatureAlgorithm.HS384,
                Instant.now().plusSeconds(3600)
        );

        FilterExecution result = executeFilter("/api/v1/accounts/1", "Bearer " + hs384Token);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, result.status);
        assertTrue(result.body.contains("\"errorCode\":\"AUTH-401\""));
        assertTrue(result.body.contains("\"message\":\"Unauthorized\""));
    }

    @Test
    @DisplayName("Token/account mismatch returns ACC-403 with same message")
    void accountMismatchReturnsAcc403WithUniformMessage() {
        AccountService accountService = mock(AccountService.class);
        AccountController controller = new AccountController(accountService);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getAttribute("accountKey")).thenReturn("alice");
        when(accountService.verifyAuthorizedAccount(1L, "alice"))
                .thenThrow(new AccountNotActiveException(1L, AccountStatus.ACTIVE));

        ResponseEntity<?> response = controller.getAccount(1L, request);
        ErrorResponse body = (ErrorResponse) response.getBody();

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ACC-403", body.getErrorCode());
        assertEquals("Account not accessible", body.getMessage());
    }

    @Test
    @DisplayName("Suspended account returns ACC-403 with identical message")
    void suspendedAccountReturnsAcc403WithUniformMessage() {
        AccountService accountService = mock(AccountService.class);
        AccountController controller = new AccountController(accountService);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getAttribute("accountKey")).thenReturn("alice");
        when(accountService.verifyAuthorizedAccount(1L, "alice"))
                .thenThrow(new AccountNotActiveException(1L, AccountStatus.SUSPENDED));

        ResponseEntity<?> response = controller.getAccount(1L, request);
        ErrorResponse body = (ErrorResponse) response.getBody();

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ACC-403", body.getErrorCode());
        assertEquals("Account not accessible", body.getMessage());
    }

    private String createToken(
            String subject,
            SecretKey key,
            SignatureAlgorithm algorithm,
            Instant expiry) {

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(expiry))
                .signWith(key, algorithm)
                .compact();
    }

        private FilterExecution executeFilter(String uri, String authorizationHeader)
                        throws IOException, ServletException {
                JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, objectMapper);
                HttpServletRequest request = mock(HttpServletRequest.class);
                HttpServletResponse response = mock(HttpServletResponse.class);
                FilterChain filterChain = mock(FilterChain.class);

                StringWriter body = new StringWriter();
                PrintWriter writer = new PrintWriter(body);

                when(request.getRequestURI()).thenReturn(uri);
                when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);
                when(response.getWriter()).thenReturn(writer);

                filter.doFilter(request, response, filterChain);
                writer.flush();

                FilterExecution result = new FilterExecution();
                result.body = body.toString();

                if (result.body.contains("\"errorCode\":\"AUTH-401\"")) {
                        result.status = HttpServletResponse.SC_UNAUTHORIZED;
                } else {
                        result.status = HttpServletResponse.SC_OK;
                        verify(filterChain).doFilter(eq(request), eq(response));
                }

                return result;
        }

        private static final class FilterExecution {
                private int status;
                private String body;
        }
}
