package com.conlact.conlact_backend;

import com.conlact.conlact_backend.dto.contact.AdminContactMessageResponse;
import com.conlact.conlact_backend.dto.contact.ContactMessageCreateRequest;
import com.conlact.conlact_backend.dto.contact.ContactMessageResponse;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.UserRole;
import com.conlact.conlact_backend.repository.ContactMessageRepository;
import com.conlact.conlact_backend.repository.ProfileRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(scripts = "/seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class ContactIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ContactMessageRepository contactMessageRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.supabase.jwt.secret}")
    private String jwtSecret;

    @Value("${app.supabase.jwt.issuer:}")
    private String jwtIssuer;

    private RestClient restClient;
    private UUID testAdminUserId;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        testAdminUserId = UUID.fromString("ba000000-0000-0000-0000-000000000001");

        jdbcTemplate.update("INSERT INTO auth.users (id) VALUES (?) ON CONFLICT (id) DO NOTHING", testAdminUserId);

        if (profileRepository.findById(testAdminUserId).isEmpty()) {
            Profile adminProfile = Profile.builder()
                    .id(testAdminUserId)
                    .fullName("Admin CONLAC-T Test")
                    .role(UserRole.admin)
                    .isActive(true)
                    .build();
            profileRepository.save(adminProfile);
        }
    }

    private String generateAdminToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        var builder = Jwts.builder()
                .subject(testAdminUserId.toString())
                .claim("email", "admin@conlact.org")
                .claim("role", "authenticated")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(60, ChronoUnit.MINUTES)));

        if (jwtIssuer != null && !jwtIssuer.isBlank()) {
            builder.issuer(jwtIssuer);
        }

        return builder.signWith(key).compact();
    }

    @Test
    @DisplayName("BE-25: Envío público anónimo de mensaje de contacto persiste en BD y permite gestión admin")
    void testPublicContactFormSubmissionAndAdminManagement() {
        // 1. Envío público anónimo mediante la ruta en español
        ContactMessageCreateRequest requestSpanish = new ContactMessageCreateRequest(
                "María Morales",
                "maria.morales@empresa.ec",
                "+593984561234",
                "Pedido institucional al por mayor",
                "Deseamos cotizar 200 unidades de queso fresco semanalmente.",
                true
        );

        var response1 = restClient.post()
                .uri("/api/contacto")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestSpanish)
                .retrieve()
                .toEntity(ContactMessageResponse.class);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ContactMessageResponse body1 = response1.getBody();
        assertThat(body1).isNotNull();
        assertThat(body1.id()).isNotNull();
        assertThat(body1.status()).isEqualTo("received");
        assertThat(body1.message()).contains("Mensaje recibido correctamente");

        // Verificar persistencia en base de datos PostgreSQL
        assertThat(contactMessageRepository.findById(body1.id())).isPresent();

        // 2. Envío público anónimo mediante el alias en inglés
        ContactMessageCreateRequest requestEnglish = new ContactMessageCreateRequest(
                "John Doe",
                "john.doe@example.com",
                null,
                "Wholesale Inquiry",
                "Requesting catalog and price list.",
                false
        );

        var response2 = restClient.post()
                .uri("/api/contact")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestEnglish)
                .retrieve()
                .toEntity(ContactMessageResponse.class);

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ContactMessageResponse body2 = response2.getBody();
        assertThat(body2).isNotNull();
        assertThat(contactMessageRepository.findById(body2.id())).isPresent();

        // 3. Ruta administrativa protegida: rechaza petición sin token (401)
        var responseUnauthorized = restClient.get()
                .uri("/api/admin/contacto")
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, resp) -> {})
                .toBodilessEntity();

        assertThat(responseUnauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 4. Ruta administrativa con token ADMIN: obtiene listado (200 OK)
        String adminToken = generateAdminToken();

        var responseAdminList = restClient.get()
                .uri("/api/admin/contacto")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<AdminContactMessageResponse>>() {});

        assertThat(responseAdminList.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<AdminContactMessageResponse> messages = responseAdminList.getBody();
        assertThat(messages).isNotEmpty();
        assertThat(messages.stream().anyMatch(m -> m.id().equals(body1.id()))).isTrue();

        // 5. Marcar mensaje como resuelto (PATCH /api/admin/contacto/{id}/resolver)
        var responseResolve = restClient.patch()
                .uri("/api/admin/contacto/{id}/resolver", body1.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .toEntity(AdminContactMessageResponse.class);

        assertThat(responseResolve.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseResolve.getBody().isResolved()).isTrue();

        var updatedFromDb = contactMessageRepository.findById(body1.id()).orElseThrow();
        assertThat(updatedFromDb.getIsResolved()).isTrue();
    }
}
