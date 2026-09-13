package com.conlact.conlact_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class JwtTokenProvider {

    private final Key verificationKey;
    private final String expectedIssuer;

    public JwtTokenProvider(
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.jwt.public-key:}") String publicKeyPem,
            @Value("${app.supabase.jwt.secret:}") String secret,
            @Value("${app.supabase.jwt.issuer:}") String issuer
    ) {
        this.verificationKey = initVerificationKey(supabaseUrl, publicKeyPem, secret);
        this.expectedIssuer = (issuer != null && !issuer.isBlank()) ? issuer.trim() : null;
    }

    private static Key initVerificationKey(String supabaseUrl, String publicKeyConfig, String secret) {
        // 1. Si se suministra una clave pública en formato PEM o Base64 X.509 directa
        if (publicKeyConfig != null && !publicKeyConfig.isBlank()) {
            PublicKey pk = tryParsePublicKey(publicKeyConfig);
            if (pk != null) {
                log.info("JWT Verification inicializado con Clave Pública Asimétrica provista manualmente");
                return pk;
            }
        }

        // 2. Si se suministra la URL de Supabase, resolver JWKS desde .well-known/jwks.json
        if (supabaseUrl != null && !supabaseUrl.isBlank() && supabaseUrl.startsWith("http")) {
            PublicKey jwksKey = tryFetchPublicKeyFromJwks(supabaseUrl);
            if (jwksKey != null) {
                log.info("JWT Verification inicializado exitosamente desde el endpoint JWKS de Supabase ({})", supabaseUrl);
                return jwksKey;
            }
        }

        // 3. Fallback a clave simétrica HMAC-SHA256 (para pruebas locales)
        if (secret != null && !secret.isBlank()) {
            log.info("JWT Verification inicializado con Clave Simétrica HMAC-SHA256 (HS256)");
            return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }

        throw new IllegalStateException("Debe configurarse app.supabase.jwt.public-key, app.supabase.url o app.supabase.jwt.secret");
    }

    private static PublicKey tryParsePublicKey(String keyStr) {
        try {
            String cleanPem = keyStr
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(cleanPem);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);

            // Intentar EC (NIST P-256)
            try {
                return KeyFactory.getInstance("EC").generatePublic(keySpec);
            } catch (Exception ecEx) {
                // Intentar RSA
                return KeyFactory.getInstance("RSA").generatePublic(keySpec);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static PublicKey tryFetchPublicKeyFromJwks(String supabaseUrl) {
        String cleanUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        String jwksUrl = cleanUrl + "/auth/v1/.well-known/jwks.json";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                String body = response.body();
                String xBase64 = extractJsonField(body, "x");
                String yBase64 = extractJsonField(body, "y");

                if (xBase64 != null && yBase64 != null) {
                    BigInteger x = new BigInteger(1, Base64.getUrlDecoder().decode(xBase64));
                    BigInteger y = new BigInteger(1, Base64.getUrlDecoder().decode(yBase64));

                    AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
                    params.init(new ECGenParameterSpec("secp256r1"));
                    ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

                    ECPoint point = new ECPoint(x, y);
                    ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, ecSpec);
                    return KeyFactory.getInstance("EC").generatePublic(pubSpec);
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener la clave pública desde JWKS ({}): {}", jwksUrl, e.getMessage());
        }
        return null;
    }

    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public Optional<Claims> parseAndValidateClaims(String token) {
        try {
            var parserBuilder = Jwts.parser();

            if (verificationKey instanceof PublicKey publicKey) {
                parserBuilder.verifyWith(publicKey);
            } else if (verificationKey instanceof SecretKey secretKey) {
                parserBuilder.verifyWith(secretKey);
            } else {
                throw new IllegalStateException("Tipo de clave de verificación no soportado: " + verificationKey.getClass().getName());
            }

            if (expectedIssuer != null) {
                parserBuilder.requireIssuer(expectedIssuer);
            }

            Claims claims = parserBuilder.build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<UUID> extractUserId(Claims claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(sub));
        } catch (IllegalArgumentException e) {
            log.warn("El subject del token no es un UUID válido: {}", sub);
            return Optional.empty();
        }
    }

    public String extractEmail(Claims claims) {
        return claims.get("email", String.class);
    }

    public Key getVerificationKey() {
        return this.verificationKey;
    }
}
