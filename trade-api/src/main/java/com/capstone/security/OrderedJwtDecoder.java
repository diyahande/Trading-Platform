package com.capstone.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Decodes JWTs with explicit verification order: signature -> expiry -> algorithm.
 */
public final class OrderedJwtDecoder implements JwtDecoder {

    private static final JWSAlgorithm REQUIRED_ALGORITHM = JWSAlgorithm.HS256;

    private final MACVerifier macVerifier;

    public OrderedJwtDecoder(String sharedSecret) {
        byte[] secretBytes = sharedSecret.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA256");
        try {
            this.macVerifier = new MACVerifier(secretKeySpec);
        } catch (JOSEException e) {
            throw new IllegalArgumentException("Invalid JWT secret configuration", e);
        }
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        SignedJWT signedJwt = parse(token);

        verifySignature(signedJwt);
        verifyExpiry(signedJwt);
        verifyAlgorithm(signedJwt);

        JWTClaimsSet claimsSet = claimsSet(signedJwt);

        Instant issuedAt = claimsSet.getIssueTime() == null ? null : claimsSet.getIssueTime().toInstant();
        Instant expiresAt = claimsSet.getExpirationTime() == null ? null : claimsSet.getExpirationTime().toInstant();
        Map<String, Object> claims = claimsSet.getClaims();

        return new Jwt(token, issuedAt, expiresAt, signedJwt.getHeader().toJSONObject(), claims);
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (Exception ex) {
            throw new JwtException("Invalid token", ex);
        }
    }

    private void verifySignature(SignedJWT signedJwt) {
        try {
            if (!signedJwt.verify(macVerifier)) {
                throw new JwtException("Invalid token");
            }
        } catch (JOSEException ex) {
            throw new JwtException("Invalid token", ex);
        }
    }

    private JWTClaimsSet claimsSet(SignedJWT signedJwt) {
        try {
            return signedJwt.getJWTClaimsSet();
        } catch (Exception ex) {
            throw new JwtException("Invalid token", ex);
        }
    }

    private void verifyExpiry(SignedJWT signedJwt) {
        JWTClaimsSet claimsSet = claimsSet(signedJwt);
        Instant now = Instant.now();
        Instant expiresAt = claimsSet.getExpirationTime() == null
                ? null
                : claimsSet.getExpirationTime().toInstant();

        if (expiresAt == null || expiresAt.isBefore(now)) {
            throw new JwtException("Invalid token");
        }
    }

    private void verifyAlgorithm(SignedJWT signedJwt) {
        if (!REQUIRED_ALGORITHM.equals(signedJwt.getHeader().getAlgorithm())) {
            throw new JwtException("Invalid token");
        }
    }
}
