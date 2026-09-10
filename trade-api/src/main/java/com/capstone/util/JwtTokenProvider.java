package com.capstone.util;

import com.capstone.exception.AuthenticationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT token validation and claim extraction.
 * Validates in order: signature, expiry, algorithm before reading claims.
 */
@Component
public class JwtTokenProvider {

    private static final String REQUIRED_ALGORITHM = "HS256";

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validate token and extract claims.
     * Order: signature → expiry → algorithm, before reading any claim.
     * Throws AuthenticationException on any failure.
     */
    public Claims validateAndGetClaims(String token) {
        try {
            // Parse token to validate signature and expiry before any claim is read.
            Jws<Claims> parsed = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);

            // Explicitly enforce the requested JWT algorithm.
            if (!REQUIRED_ALGORITHM.equals(parsed.getHeader().getAlgorithm())) {
                throw new AuthenticationException("Unsupported token algorithm");
            }

            return parsed.getBody();
        } catch (SignatureException e) {
            throw new AuthenticationException("Invalid token signature");
        } catch (ExpiredJwtException e) {
            throw new AuthenticationException("Token has expired");
        } catch (UnsupportedJwtException e) {
            throw new AuthenticationException("Unsupported token algorithm");
        } catch (MalformedJwtException | IllegalArgumentException e) {
            throw new AuthenticationException("Invalid token format");
        } catch (JwtException e) {
            throw new AuthenticationException("Token validation failed");
        }
    }

    /**
     * Validate token and extract subject after successful validation.
     */
    public String validateAndGetSubject(String token) {
        Claims claims = validateAndGetClaims(token);
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AuthenticationException("Token subject missing");
        }
        return subject;
    }

    /**
     * Extract roles claim from validated token.
     * Returns empty list if token is invalid (caller should validate first).
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        try {
            Claims claims = validateAndGetClaims(token);
            return claims.get("roles", List.class);
        } catch (AuthenticationException e) {
            return List.of();
        }
    }
}
