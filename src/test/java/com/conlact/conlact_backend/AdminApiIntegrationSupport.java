package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Sql(scripts = "/seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
abstract class AdminApiIntegrationSupport extends BaseIntegrationTest {
    protected static final UUID ADMIN_ID = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    @LocalServerPort private int port;
    @Autowired protected JdbcTemplate jdbc;
    @Value("${app.supabase.jwt.secret}") private String jwtSecret;
    @Value("${app.supabase.jwt.issuer:}") private String jwtIssuer;
    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient client;
    protected String token;

    @BeforeEach
    void configureClientAndAdministrator() {
        jdbc.update("INSERT INTO auth.users(id) VALUES (?) ON CONFLICT DO NOTHING", ADMIN_ID);
        jdbc.update("""
                INSERT INTO public.profiles(id, full_name, role, is_active) VALUES (?, 'Admin BE-15/BE-19', 'admin', true)
                ON CONFLICT(id) DO UPDATE SET is_active=true
                """, ADMIN_ID);
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        token = tokenFor(ADMIN_ID);
    }

    protected String tokenFor(UUID userId) {
        var builder = Jwts.builder().subject(userId.toString()).claim("role", "authenticated")
                .issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(600)));
        if (!jwtIssuer.isBlank()) {
            builder.issuer(jwtIssuer);
        }
        return builder.signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    protected ResponseEntity<String> request(HttpMethod method, String path, Object body, String authToken) throws Exception {
        var request = client.method(method).uri(path);
        if (authToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body instanceof String text ? text : mapper.writeValueAsString(body));
        }
        return request.retrieve().onStatus(HttpStatusCode::isError, (req, response) -> {
            // Las pruebas verifican explícitamente el código y cuerpo de los errores esperados.
        }).toEntity(String.class);
    }

    protected JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }
}
