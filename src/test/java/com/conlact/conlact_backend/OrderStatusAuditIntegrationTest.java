package com.conlact.conlact_backend;

import com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest;
import com.conlact.conlact_backend.dto.order.OrderStatusUpdateResponse;
import com.conlact.conlact_backend.entity.Order;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.DeliveryMethod;
import com.conlact.conlact_backend.entity.enums.OrderStatus;
import com.conlact.conlact_backend.entity.enums.PaymentMethod;
import com.conlact.conlact_backend.entity.enums.UserRole;
import com.conlact.conlact_backend.repository.OrderRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(scripts = "/seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class OrderStatusAuditIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrderRepository orderRepository;

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
                    .fullName("Admin General CONLAC-T")
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
    @DisplayName("BE-33: Transición de estado vía PATCH registra cambios en audit_log y actualiza la orden")
    void testAdminOrderStatusTransitionAndAuditLogging() {
        // 1. Crear pedido inicial en estado PENDIENTE
        Order order = Order.builder()
                .customerName("María Morales")
                .customerTaxId("1801234567")
                .customerPhone("+593984561234")
                .customerEmail("maria.morales@empresa.ec")
                .deliveryMethod(DeliveryMethod.pickup)
                .paymentMethod(PaymentMethod.bank_transfer)
                .subtotal(new BigDecimal("25.00"))
                .shippingCost(BigDecimal.ZERO)
                .total(new BigDecimal("25.00"))
                .status(OrderStatus.pending)
                .build();

        Order saved = orderRepository.saveAndFlush(order);
        UUID orderId = saved.getId();

        String adminToken = generateAdminToken();

        // 2. Realizar petición PATCH con token administrativo (Semana 7 / BE-33)
        OrderStatusUpdateRequest updateRequest = OrderStatusUpdateRequest.builder()
                .newStatus("preparing")
                .administrativeNotes("Transferencia Banco Pichincha #458921 confirmada en cuenta.")
                .build();

        var response = restClient.patch()
                .uri("/api/admin/pedidos/{id}/estado", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateRequest)
                .retrieve()
                .toEntity(OrderStatusUpdateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrderStatusUpdateResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(orderId);
        assertThat(body.previousStatus()).isEqualTo("pending");
        assertThat(body.currentStatus()).isEqualTo("preparing");
        assertThat(body.administrativeNotes()).contains("Transferencia Banco Pichincha #458921");

        // 3. Verificar persistencia en base de datos de la orden
        Order updatedInDb = orderRepository.findById(orderId).orElseThrow();
        assertThat(updatedInDb.getStatus()).isEqualTo(OrderStatus.preparing);
        assertThat(updatedInDb.getAdministrativeNotes()).contains("Transferencia Banco Pichincha #458921");

        // 4. Verificar inserción inmutable en tabla audit_log
        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                """
                SELECT actor_user_id, entity_name, entity_id, action,
                       old_data->>'status' AS old_status,
                       new_data->>'status' AS new_status,
                       new_data->>'usuario' AS audit_user,
                       new_data->>'rol' AS audit_role,
                       new_data->>'notas_administrativas' AS audit_notes
                FROM audit_log
                WHERE entity_name = 'orders' AND entity_id = ?
                ORDER BY created_at DESC LIMIT 1
                """,
                orderId.toString()
        );

        assertThat(auditRow).isNotNull();
        assertThat(auditRow.get("entity_name")).isEqualTo("orders");
        assertThat(auditRow.get("entity_id")).isEqualTo(orderId.toString());
        assertThat(auditRow.get("action")).isEqualTo("ORDER_STATUS_UPDATE");
        assertThat(auditRow.get("old_status")).isEqualTo("pending");
        assertThat(auditRow.get("new_status")).isEqualTo("preparing");
        assertThat(auditRow.get("audit_user")).isEqualTo("admin@conlact.org");
        assertThat(auditRow.get("audit_role")).isEqualTo("admin");
        assertThat(auditRow.get("audit_notes")).isEqualTo("Transferencia Banco Pichincha #458921 confirmada en cuenta.");
    }

    @Test
    @DisplayName("BE-33: Petición anónima sin token administrativo es rechazada con 401 Unauthorized")
    void testAnonymousRequestShouldBeRejectedWith401() {
        UUID randomId = UUID.randomUUID();
        OrderStatusUpdateRequest updateRequest = OrderStatusUpdateRequest.builder()
                .newStatus("paid")
                .build();

        var response = restClient.patch()
                .uri("/api/admin/pedidos/{id}/estado", randomId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateRequest)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, resp) -> {
                    // Esperado 401
                })
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("BE-33: Transición inválida es rechazada con 400 Bad Request y no genera auditoría")
    void testInvalidTransitionRejectedAndDoesNotCreateAuditLog() {
        Order order = Order.builder()
                .customerName("Carlos Andrade")
                .customerTaxId("1809988776")
                .customerPhone("+593988776655")
                .deliveryMethod(DeliveryMethod.pickup)
                .paymentMethod(PaymentMethod.bank_transfer)
                .subtotal(new BigDecimal("15.00"))
                .shippingCost(BigDecimal.ZERO)
                .total(new BigDecimal("15.00"))
                .status(OrderStatus.completed)
                .build();

        Order saved = orderRepository.saveAndFlush(order);
        UUID orderId = saved.getId();
        String adminToken = generateAdminToken();

        OrderStatusUpdateRequest invalidRequest = OrderStatusUpdateRequest.builder()
                .newStatus("pending")
                .build();

        var response = restClient.patch()
                .uri("/api/admin/pedidos/{id}/estado", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalidRequest)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, resp) -> {
                    // Esperado 400
                })
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Confirmar que no se creó registro espurio en audit_log
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_name = 'orders' AND entity_id = ?",
                Integer.class,
                orderId.toString()
        );
        assertThat(count).isEqualTo(0);
    }
}
