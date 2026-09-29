package com.conlact.conlact_backend;

import com.conlact.conlact_backend.dto.association.image.AssociationGalleryResponse;
import com.conlact.conlact_backend.dto.association.image.AssociationImageItemResponse;
import com.conlact.conlact_backend.dto.association.image.CreateAssociationImageRequest;
import com.conlact.conlact_backend.dto.association.image.ImageOrderItemRequest;
import com.conlact.conlact_backend.dto.association.image.ReorderAssociationImagesRequest;
import com.conlact.conlact_backend.dto.association.image.UpdateAssociationImageRequest;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.AssociationImageType;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
public class AssociationImageIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

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
    private UUID elLinderoId;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        testAdminUserId = UUID.fromString("ba000000-0000-0000-0000-000000000001");
        elLinderoId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

        // Asegurar usuario auth y perfil admin activo
        jdbcTemplate.update("INSERT INTO auth.users (id) VALUES (?) ON CONFLICT (id) DO NOTHING", testAdminUserId);

        if (profileRepository.findById(testAdminUserId).isEmpty()) {
            Profile adminProfile = Profile.builder()
                    .id(testAdminUserId)
                    .fullName("Admin CONLAC-T Integración")
                    .role(UserRole.admin)
                    .isActive(true)
                    .build();
            profileRepository.save(adminProfile);
        }
    }

    private String generateTestSupabaseToken(UUID userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", "authenticated")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(60, ChronoUnit.MINUTES)));

        if (jwtIssuer != null && !jwtIssuer.isBlank()) {
            builder.issuer(jwtIssuer);
        }

        return builder.signWith(key).compact();
    }

    @Test
    @DisplayName("TC-BE14-19: Petición anónima de escritura (POST) debe ser rechazada con 401 Unauthorized")
    void anonymousPostShouldBeUnauthorized() {
        CreateAssociationImageRequest req = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/lindero/instalaciones/planta.jpg")
                .imageType(AssociationImageType.facility)
                .build();

        var response = restClient.post()
                .uri("/api/associations/{associationId}/fotos", elLinderoId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, resp) -> {})
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("TC-BE14-20 & TC-BE14-01: Administrador activo registra foto (.jpg) y recibe 201 Created")
    void adminCanCreateImageSuccess() {
        String token = generateTestSupabaseToken(testAdminUserId, "admin@conlact.org");

        CreateAssociationImageRequest req = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/lindero/instalaciones/planta-01.jpg")
                .imageType(AssociationImageType.facility)
                .altText("Instalaciones de El Lindero")
                .sortOrder(1)
                .build();

        var response = restClient.post()
                .uri("/api/associations/{associationId}/fotos", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .toEntity(AssociationImageItemResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUrl()).isEqualTo("https://storage.conlact.com/lindero/instalaciones/planta-01.jpg");
        assertThat(response.getBody().getImageType()).isEqualTo(AssociationImageType.facility);
        assertThat(response.getBody().getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-BE14-12: Impedir URL duplicada en la misma asociación retorna 409 Conflict")
    void duplicateUrlInSameAssociationReturnsConflict() {
        String token = generateTestSupabaseToken(testAdminUserId, "admin@conlact.org");

        CreateAssociationImageRequest req = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/lindero/sellos/arcsa.png")
                .imageType(AssociationImageType.seal)
                .build();

        // Primera inserción
        restClient.post()
                .uri("/api/associations/{associationId}/fotos", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .toBodilessEntity();

        // Segunda inserción con la misma URL
        var response = restClient.post()
                .uri("/api/associations/{associationId}/fotos", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, resp) -> {})
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Flujo completo BE-14: Registrar, Consultar ordenada, Actualizar, Reordenar y Desvincular")
    void fullLifecycleTest() {
        String token = generateTestSupabaseToken(testAdminUserId, "admin@conlact.org");

        // 1. Crear 2 imágenes
        CreateAssociationImageRequest req1 = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/lindero/productores/productor-1.webp")
                .imageType(AssociationImageType.producer)
                .sortOrder(1)
                .build();

        CreateAssociationImageRequest req2 = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/lindero/sellos/agrocalidad.png")
                .imageType(AssociationImageType.seal)
                .sortOrder(2)
                .build();

        var img1 = restClient.post()
                .uri("/api/associations/{associationId}/fotos", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req1)
                .retrieve()
                .body(AssociationImageItemResponse.class);

        var img2 = restClient.post()
                .uri("/api/associations/{associationId}/fotos", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req2)
                .retrieve()
                .body(AssociationImageItemResponse.class);

        assertThat(img1).isNotNull();
        assertThat(img2).isNotNull();

        // 2. Consulta pública de la galería
        var galleryResp = restClient.get()
                .uri("/api/associations/{associationId}/fotos", elLinderoId)
                .retrieve()
                .body(AssociationGalleryResponse.class);

        assertThat(galleryResp).isNotNull();
        assertThat(galleryResp.getImages()).hasSizeGreaterThanOrEqualTo(2);

        // 3. Actualizar metadatos de la imagen 1 (PATCH)
        UpdateAssociationImageRequest updateReq = UpdateAssociationImageRequest.builder()
                .altText("Foto de equipo de campo")
                .build();

        var updatedImg = restClient.patch()
                .uri("/api/associations/{associationId}/fotos/{imageId}", elLinderoId, img1.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateReq)
                .retrieve()
                .body(AssociationImageItemResponse.class);

        assertThat(updatedImg).isNotNull();
        assertThat(updatedImg.getAltText()).isEqualTo("Foto de equipo de campo");

        // 4. Reordenar galería
        ReorderAssociationImagesRequest reorderReq = ReorderAssociationImagesRequest.builder()
                .images(List.of(
                        new ImageOrderItemRequest(img2.getId(), 0),
                        new ImageOrderItemRequest(img1.getId(), 1)
                ))
                .build();

        var reorderedGallery = restClient.patch()
                .uri("/api/associations/{associationId}/fotos/orden", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reorderReq)
                .retrieve()
                .body(AssociationGalleryResponse.class);

        assertThat(reorderedGallery).isNotNull();
        assertThat(reorderedGallery.getImages().get(0).getId()).isEqualTo(img2.getId());

        // 5. Desvincular imagen (DELETE)
        var deleteResp = restClient.delete()
                .uri("/api/associations/{associationId}/fotos/{imageId}", elLinderoId, img1.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 6. Verificar que la imagen 1 ya no esté en la galería
        var finalGallery = restClient.get()
                .uri("/api/associations/{associationId}/fotos", elLinderoId)
                .retrieve()
                .body(AssociationGalleryResponse.class);

        assertThat(finalGallery).isNotNull();
        assertThat(finalGallery.getImages().stream().noneMatch(i -> i.getId().equals(img1.getId()))).isTrue();
    }
}
