package com.capstone.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class OrderedJwtDecoderTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-min-32-bytes";

    @Test
    void decode_validToken_returnsJwt() throws Exception {
        OrderedJwtDecoder decoder = new OrderedJwtDecoder(SECRET);
        String token = buildToken(SECRET, JWSAlgorithm.HS256, Instant.now().plusSeconds(120));

        Jwt jwt = decoder.decode(token);

        assertEquals("acc-1", jwt.getSubject());
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
    }

    @Test
    void decode_invalidTokenFormat_throws() {
        OrderedJwtDecoder decoder = new OrderedJwtDecoder(SECRET);

        assertThrows(JwtException.class, () -> decoder.decode("not-a-jwt"));
    }

    @Test
    void decode_invalidSignature_throws() throws Exception {
        OrderedJwtDecoder decoder = new OrderedJwtDecoder(SECRET);
        String token = buildToken("another-secret-key-for-jwt-signing-123", JWSAlgorithm.HS256, Instant.now().plusSeconds(120));

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decode_missingExpiry_throws() throws Exception {
        OrderedJwtDecoder decoder = new OrderedJwtDecoder(SECRET);
        String token = buildTokenWithoutExpiry();

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decode_expiredToken_throws() throws Exception {
        OrderedJwtDecoder decoder = new OrderedJwtDecoder(SECRET);
        String token = buildToken(SECRET, JWSAlgorithm.HS256, Instant.now().minusSeconds(10));

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decode_wrongAlgorithm_throws() throws Exception {
        String longSecret = "test-secret-key-for-jwt-signing-min-32-bytes-plus-extra-length";
        OrderedJwtDecoder decoder = new OrderedJwtDecoder(longSecret);
        String token = buildToken(longSecret, JWSAlgorithm.HS384, Instant.now().plusSeconds(120));

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void constructor_shortSecret_throws() {
        assertThrows(IllegalArgumentException.class, () -> new OrderedJwtDecoder("short"));
    }

    private static String buildToken(String secret, JWSAlgorithm algorithm, Instant expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("acc-1")
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(expiry))
                .claim("role", "USER")
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(new MACSigner(secret.getBytes()));
        return jwt.serialize();
    }

    private static String buildTokenWithoutExpiry() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("acc-1")
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes()));
        return jwt.serialize();
    }
}



