package com.conlact.conlact_backend;

import com.conlact.conlact_backend.dto.product.image.ProductImageCreateRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageReorderItemRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageReorderRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageResponse;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.entity.Category;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.conlact.conlact_backend.entity.enums.UserRole;
import com.conlact.conlact_backend.repository.AssociationRepository;
import com.conlact.conlact_backend.repository.CategoryRepository;
import com.conlact.conlact_backend.repository.ProductImageRepository;
import com.conlact.conlact_backend.repository.ProductRepository;
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
public class ProductImageIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
    private Product testProduct;

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

        Association assoc = associationRepository.findAll().stream().findFirst().orElse(null);
        Category cat = categoryRepository.findAll().stream().findFirst().orElse(null);

        Product prod = Product.builder()
                .name("Queso Fresco Test BE20 " + UUID.randomUUID().toString().substring(0, 6))
                .slug("queso-fresco-test-be20-" + UUID.randomUUID().toString().substring(0, 6))
                .association(assoc)
                .category(cat)
                .cheeseType("Fresco")
                .status(ProductStatus.published)
                .build();

        testProduct = productRepository.save(prod);
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
    @DisplayName("BE-20 Integración: Flujo completo de galería (agregar, portada, reordenar, eliminar individual y cascada huérfanas)")
    void testCompleteProductImageGalleryFlow() {
        String adminToken = generateAdminToken();

        // 1. Agregar primera imagen (automáticamente portada / sort_order=0)
        ProductImageCreateRequest req1 = new ProductImageCreateRequest(
                "products/" + testProduct.getId() + "/frontal.webp",
                "Fotografía frontal del queso",
                null,
                null
        );

        var resp1 = restClient.post()
                .uri("/api/admin/productos/{id}/imagenes", testProduct.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req1)
                .retrieve()
                .toEntity(ProductImageResponse.class);

        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProductImageResponse img1 = resp1.getBody();
        assertThat(img1).isNotNull();
        assertThat(img1.sortOrder()).isEqualTo(0);
        assertThat(img1.isPrimary()).isTrue();

        // 2. Agregar segunda imagen
        ProductImageCreateRequest req2 = new ProductImageCreateRequest(
                "products/" + testProduct.getId() + "/corte.webp",
                "Fotografía del corte transversal",
                null,
                false
        );

        var resp2 = restClient.post()
                .uri("/api/admin/productos/{id}/imagenes", testProduct.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req2)
                .retrieve()
                .toEntity(ProductImageResponse.class);

        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProductImageResponse img2 = resp2.getBody();
        assertThat(img2).isNotNull();
        assertThat(img2.sortOrder()).isEqualTo(1);
        assertThat(img2.isPrimary()).isFalse();

        // 3. Establecer la segunda imagen como portada (PATCH .../principal)
        var respPrimary = restClient.patch()
                .uri("/api/admin/productos/{id}/imagenes/{imageId}/principal", testProduct.getId(), img2.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .toEntity(ProductImageResponse.class);

        assertThat(respPrimary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respPrimary.getBody().id()).isEqualTo(img2.id());
        assertThat(respPrimary.getBody().sortOrder()).isEqualTo(0);
        assertThat(respPrimary.getBody().isPrimary()).isTrue();

        // 4. Listar galería pública
        var respPublicList = restClient.get()
                .uri("/api/productos/{id}/imagenes", testProduct.getId())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<ProductImageResponse>>() {});

        assertThat(respPublicList.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ProductImageResponse> publicList = respPublicList.getBody();
        assertThat(publicList).hasSize(2);
        assertThat(publicList.get(0).id()).isEqualTo(img2.id());
        assertThat(publicList.get(0).isPrimary()).isTrue();
        assertThat(publicList.get(1).id()).isEqualTo(img1.id());
        assertThat(publicList.get(1).isPrimary()).isFalse();

        // 5. Reordenar galería (PUT .../orden)
        ProductImageReorderRequest reorderReq = new ProductImageReorderRequest(List.of(
                new ProductImageReorderItemRequest(img1.id(), 0),
                new ProductImageReorderItemRequest(img2.id(), 1)
        ));

        var respReorder = restClient.put()
                .uri("/api/admin/productos/{id}/imagenes/orden", testProduct.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reorderReq)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<ProductImageResponse>>() {});

        assertThat(respReorder.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ProductImageResponse> reorderedList = respReorder.getBody();
        assertThat(reorderedList.get(0).id()).isEqualTo(img1.id());
        assertThat(reorderedList.get(0).sortOrder()).isEqualTo(0);
        assertThat(reorderedList.get(0).isPrimary()).isTrue();

        // 6. Eliminar imagen individual
        var respDelete = restClient.delete()
                .uri("/api/admin/productos/{id}/imagenes/{imageId}", testProduct.getId(), img1.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(respDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(productImageRepository.findById(img1.id())).isEmpty();

        // 7. Eliminación en cascada de imágenes huérfanas al borrar el producto padre
        productRepository.deleteById(testProduct.getId());
        assertThat(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(testProduct.getId())).isEmpty();
    }
}
