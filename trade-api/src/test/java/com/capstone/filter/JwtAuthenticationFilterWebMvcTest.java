package com.capstone.filter;

import com.capstone.config.SecurityConfig;
import com.capstone.controller.AccountController;
import com.capstone.enums.AccountStatus;
import com.capstone.exception.AccountNotActiveException;
import com.capstone.exception.GlobalExceptionHandler;
import com.capstone.service.AccountService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=mission-control-shared-secret-key-32-bytes-minimum",
        "jwt.principal-claim=sub"
})
class JwtAuthenticationFilterWebMvcTest {

    private static final byte[] SHARED_SECRET =
            "mission-control-shared-secret-key-32-bytes-minimum".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

        @MockitoBean
    private AccountService accountService;

    @Test
    @DisplayName("Missing Authorization header returns uniform AUTH-401")
    void missingAuthorizationHeaderReturnsAuth401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @DisplayName("Wrong Authorization scheme returns uniform AUTH-401")
    void wrongAuthorizationSchemeReturnsAuth401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Basic abc123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @DisplayName("Expired token returns uniform AUTH-401")
    void expiredTokenReturnsAuth401() throws Exception {
        String expiredToken = mintToken("Alice Johnson", JWSAlgorithm.HS256, SHARED_SECRET, Instant.now().minusSeconds(60));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @DisplayName("Forged signature token returns uniform AUTH-401")
    void forgedSignatureReturnsAuth401() throws Exception {
        byte[] forgedSecret = "another-shared-secret-key-32bytes!!".getBytes(StandardCharsets.UTF_8);
        String forgedToken = mintToken("Alice Johnson", JWSAlgorithm.HS256, forgedSecret, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @DisplayName("Wrong algorithm token returns uniform AUTH-401")
    void wrongAlgorithmReturnsAuth401() throws Exception {
        String hs384Token = mintToken("Alice Johnson", JWSAlgorithm.HS384, SHARED_SECRET, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hs384Token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @DisplayName("Token/account mismatch returns ACC-403 with suspended-account message")
    void accountMismatchReturnsAcc403WithSuspendedMessage() throws Exception {
        when(accountService.verifyAuthorizedAccount(1L, "Alice Johnson"))
                .thenThrow(new AccountNotActiveException(1L, AccountStatus.ACTIVE));

        String validToken = mintToken("Alice Johnson", JWSAlgorithm.HS256, SHARED_SECRET, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"))
                .andExpect(jsonPath("$.message").value("Account not active"));
    }

    @Test
    @DisplayName("Suspended account returns ACC-403 with same message")
    void suspendedAccountReturnsAcc403WithSameMessage() throws Exception {
        when(accountService.verifyAuthorizedAccount(1L, "Alice Johnson"))
                .thenThrow(new AccountNotActiveException(1L, AccountStatus.SUSPENDED));

        String validToken = mintToken("Alice Johnson", JWSAlgorithm.HS256, SHARED_SECRET, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"))
                .andExpect(jsonPath("$.message").value("Account not active"));
    }

    private static String mintToken(String subject, JWSAlgorithm algorithm, byte[] secret, Instant expiry)
            throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(new Date())
                .expirationTime(Date.from(expiry))
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(),
                claims
        );

        JWSSigner signer = new MACSigner(secret);
        signedJwt.sign(signer);

        return signedJwt.serialize();
    }
}
