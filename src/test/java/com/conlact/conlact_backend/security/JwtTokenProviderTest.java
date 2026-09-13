package com.conlact.conlact_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    @Test
    @DisplayName("Debe validar tokens firmados con clave ECC (P-256 / ES256) usando la clave pública PEM")
    void shouldValidateTokenWithEccP256PublicKey() throws Exception {
        // Generar par de llaves EC con curva NIST P-256 (secp256r1) igual que Supabase
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = kpg.generateKeyPair();

        String pemPublicKey = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        UUID expectedUserId = UUID.randomUUID();
        String expectedEmail = "admin@conlact.ec";

        // Supabase firma el token con la clave privada usando ES256
        String token = Jwts.builder()
                .subject(expectedUserId.toString())
                .claim("email", expectedEmail)
                .signWith(keyPair.getPrivate(), Jwts.SIG.ES256)
                .compact();

        // El backend valida usando la clave pública
        JwtTokenProvider provider = new JwtTokenProvider("", pemPublicKey, "", "");

        Optional<Claims> claimsOpt = provider.parseAndValidateClaims(token);
        assertThat(claimsOpt).isPresent();

        Claims claims = claimsOpt.get();
        assertThat(provider.extractUserId(claims)).contains(expectedUserId);
        assertThat(provider.extractEmail(claims)).isEqualTo(expectedEmail);
    }

    @Test
    @DisplayName("Debe validar tokens firmados con secreto simétrico HMAC-SHA256 (HS256) como fallback")
    void shouldValidateTokenWithHmacSecretFallback() {
        String secret = "super-secret-jwt-token-with-at-least-32-characters-length";
        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        UUID expectedUserId = UUID.randomUUID();
        String expectedEmail = "operador@conlact.ec";

        String token = Jwts.builder()
                .subject(expectedUserId.toString())
                .claim("email", expectedEmail)
                .signWith(secretKey)
                .compact();

        JwtTokenProvider provider = new JwtTokenProvider("", "", secret, "");

        Optional<Claims> claimsOpt = provider.parseAndValidateClaims(token);
        assertThat(claimsOpt).isPresent();

        Claims claims = claimsOpt.get();
        assertThat(provider.extractUserId(claims)).contains(expectedUserId);
        assertThat(provider.extractEmail(claims)).isEqualTo(expectedEmail);
    }

    @Test
    @DisplayName("Debe rechazar tokens alterados o con firma inválida")
    void shouldRejectInvalidOrTamperedToken() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair1 = kpg.generateKeyPair();
        KeyPair keyPair2 = kpg.generateKeyPair();

        String pemPublicKey1 = Base64.getEncoder().encodeToString(keyPair1.getPublic().getEncoded());

        // Firmado con keyPair2 (distinta)
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .signWith(keyPair2.getPrivate(), Jwts.SIG.ES256)
                .compact();

        JwtTokenProvider provider = new JwtTokenProvider("", pemPublicKey1, "", "");

        Optional<Claims> claimsOpt = provider.parseAndValidateClaims(token);
        assertThat(claimsOpt).isEmpty();
    }
}
