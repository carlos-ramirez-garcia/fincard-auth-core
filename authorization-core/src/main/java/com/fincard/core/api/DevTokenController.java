package com.fincard.core.api;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * EMISOR de tokens de prueba — SOLO con perfil "local" (docker compose).
 * En producción reemplazo: un IdP real (Keycloak, Auth0, etc.). (ADR)
 */
@RestController
@Profile("local")
@RequestMapping("/api/v1/dev")
public class DevTokenController {

    @Value("${fincard.security.jwt.secret}")
    private String jwtSecret;

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    @PostMapping("/tokens")
    public TokenResponse issue() throws Exception {
        var header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build();

        var claims = new JWTClaimsSet.Builder()
                .subject("fincard-merchant-client")
                .issuer("fincard-dev")
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))  // 1 hora
                .claim("scope", "authorizations:write cards:read")
                .build();

        var signer = new MACSigner(
                new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var signedJwt = new SignedJWT(header, claims);
        signedJwt.sign(signer);

        return new TokenResponse(signedJwt.serialize(), "Bearer", 3600);
    }
}