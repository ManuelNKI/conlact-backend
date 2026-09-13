package com.conlact.conlact_backend;

import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.UserRole;
import com.conlact.conlact_backend.repository.ProfileRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.supabase.jwt.secret}")
    private String jwtSecret;

    private RestClient restClient;
    private UUID testAdminUserId;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        testAdminUserId = UUID.fromString("ba000000-0000-0000-0000-000000000001");

        // Insertar en auth.users para satisfacer la foreign key de profiles
        jdbcTemplate.update("INSERT INTO auth.users (id) VALUES (?) ON CONFLICT (id) DO NOTHING", testAdminUserId);

        // Asegurar que exista un perfil admin activo para el usuario de prueba
        if (profileRepository.findById(testAdminUserId).isEmpty()) {
            Profile adminProfile = Profile.builder()
                    .id(testAdminUserId)
                    .fullName("Administrador CONLAC-T Test")
                    .role(UserRole.admin)
                    .isActive(true)
                    .build();
            profileRepository.save(adminProfile);
        }
    }

    private String generateTestSupabaseToken(UUID userId, String email, String role, long validMinutes) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Rutas protegidas deben rechazar peticiones sin token (401)")
    void shouldRejectUnauthenticatedRequestToAdminRoutes() {
        var response = restClient.get()
                .uri("/api/admin/metrics")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    // Esperado
                })
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Rutas protegidas deben rechazar peticiones con token inválido o manipulado (401)")
    void shouldRejectInvalidJwtToken() {
        var response = restClient.get()
                .uri("/api/admin/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido-y-falso")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    // Esperado
                })
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Rutas protegidas permiten acceso con token JWT de Supabase válido y perfil admin activo (no devuelve 401)")
    void shouldAcceptValidSupabaseTokenForAdmin() {
        String token = generateTestSupabaseToken(
                testAdminUserId,
                "admin@conlact.org",
                "authenticated",
                60
        );

        var response = restClient.get()
                .uri("/api/admin/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    // Si no existe el endpoint aún, devolverá 404 Not Found, pero NUNCA 401 Unauthorized
                })
                .toBodilessEntity();

        // 404 confirma que el filtro de seguridad dejó pasar la petición y llegó al dispatcher de Spring MVC
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
