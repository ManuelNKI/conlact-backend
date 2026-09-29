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

    @Test
    @DisplayName("GET galería pública para asociación existente pero no publicada (is_published=false) devuelve 404")
    void unpublishedAssociationReturnsNotFound() {
        // En seed.sql podemos crear una asociación no publicada o verificar que devuelva 404
        UUID unpublishedId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO public.associations (id, slug, name, is_published)
            VALUES (?, ?, ?, false)
            """, unpublishedId, "asociacion-oculta-" + unpublishedId.toString().substring(0, 8), "Asociación Oculta");

        var response = restClient.get()
                .uri("/api/associations/{associationId}/images", unpublishedId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH reordenar con IDs duplicados en el cuerpo devuelve 400 Bad Request")
    void reorderWithDuplicateIdsReturnsBadRequest() {
        String token = generateTestSupabaseToken(testAdminUserId, "admin@conlact.org");
        UUID dummyId = UUID.randomUUID();

        ReorderAssociationImagesRequest reorderReq = ReorderAssociationImagesRequest.builder()
                .images(List.of(
                        new ImageOrderItemRequest(dummyId, 1),
                        new ImageOrderItemRequest(dummyId, 2)
                ))
                .build();

        var response = restClient.patch()
                .uri("/api/associations/{associationId}/images/orden", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reorderReq)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Punto 5: PATCH reordenar con una imagen perteneciente a otra asociación devuelve 404 y hace rollback")
    void reorderWithForeignImageReturnsNotFoundAndRollsBack() {
        String token = generateTestSupabaseToken(testAdminUserId, "admin@conlact.org");
        UUID mulanleoId = UUID.fromString("a0000000-0000-0000-0000-000000000002");

        // Crear imagen en El Lindero
        CreateAssociationImageRequest reqLindero = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/lindero/planta-atomic.jpg")
                .imageType(AssociationImageType.facility)
                .sortOrder(10)
                .build();
        var imgLindero = restClient.post()
                .uri("/api/associations/{associationId}/images", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reqLindero)
                .retrieve()
                .body(AssociationImageItemResponse.class);

        // Crear imagen en Mulanleo (otra asociación)
        CreateAssociationImageRequest reqMulanleo = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/mulanleo/planta-atomic.jpg")
                .imageType(AssociationImageType.facility)
                .sortOrder(10)
                .build();
        var imgMulanleo = restClient.post()
                .uri("/api/associations/{associationId}/images", mulanleoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reqMulanleo)
                .retrieve()
                .body(AssociationImageItemResponse.class);

        // Intentar reordenar en El Lindero incluyendo la imagen de Mulanleo
        ReorderAssociationImagesRequest reorderReq = ReorderAssociationImagesRequest.builder()
                .images(List.of(
                        new ImageOrderItemRequest(imgLindero.getId(), 1),
                        new ImageOrderItemRequest(imgMulanleo.getId(), 2)
                ))
                .build();

        var response = restClient.patch()
                .uri("/api/associations/{associationId}/images/orden", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reorderReq)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verificar atomicidad / rollback: el sortOrder de imgLindero debe permanecer en 10
        var gallery = restClient.get()
                .uri("/api/associations/{associationId}/images", elLinderoId)
                .retrieve()
                .body(AssociationGalleryResponse.class);

        var persistedLindero = gallery.getImages().stream()
                .filter(i -> i.getId().equals(imgLindero.getId()))
                .findFirst().orElseThrow();
        assertThat(persistedLindero.getSortOrder()).isEqualTo(10);
    }

    @Test
    @DisplayName("Punto 6: Validar alt_text (válido 200, vacío 400, >255 400, y PATCH {} sin cambios 200)")
    void altTextAndPartialUpdateValidations() {
        String token = generateTestSupabaseToken(testAdminUserId, "admin@conlact.org");

        // 1. Crear imagen inicial
        CreateAssociationImageRequest createReq = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/lindero/instalaciones/alt-test.jpg")
                .imageType(AssociationImageType.facility)
                .altText("Texto original")
                .sortOrder(5)
                .build();

        var created = restClient.post()
                .uri("/api/associations/{associationId}/images", elLinderoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(createReq)
                .retrieve()
                .body(AssociationImageItemResponse.class);

        // 2. PATCH con alt_text válido -> 200
        UpdateAssociationImageRequest validAlt = UpdateAssociationImageRequest.builder()
                .altText("Planta de procesamiento de El Lindero")
                .build();
        var respValid = restClient.patch()
                .uri("/api/associations/{associationId}/images/{imageId}", elLinderoId, created.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(validAlt)
                .retrieve()
                .body(AssociationImageItemResponse.class);
        assertThat(respValid.getAltText()).isEqualTo("Planta de procesamiento de El Lindero");

        // 3. PATCH con alt_text de solo espacios "   " -> 400 Bad Request
        UpdateAssociationImageRequest blankAlt = UpdateAssociationImageRequest.builder()
                .altText("   ")
                .build();
        var respBlank = restClient.patch()
                .uri("/api/associations/{associationId}/images/{imageId}", elLinderoId, created.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(blankAlt)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toBodilessEntity();
        assertThat(respBlank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 4. PATCH con alt_text > 255 caracteres -> 400 Bad Request
        UpdateAssociationImageRequest oversizedAlt = UpdateAssociationImageRequest.builder()
                .altText("a".repeat(256))
                .build();
        var respOversized = restClient.patch()
                .uri("/api/associations/{associationId}/images/{imageId}", elLinderoId, created.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(oversizedAlt)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toBodilessEntity();
        assertThat(respOversized.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 5. PATCH con cuerpo vacío {} -> 200 y no cambia los datos existentes (preserva el alt_text)
        var respEmptyBody = restClient.patch()
                .uri("/api/associations/{associationId}/images/{imageId}", elLinderoId, created.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(AssociationImageItemResponse.class);
        assertThat(respEmptyBody.getAltText()).isEqualTo("Planta de procesamiento de El Lindero");
        assertThat(respEmptyBody.getUrl()).isEqualTo(created.getUrl());
    }
}
