package com.conlact.conlact_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BE-39: Pruebas de Monitoreo de Salud con Spring Boot Actuator")
public class ActuatorHealthIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("BE-39: GET /actuator/health es público, responde 200 OK y reporta status UP")
    void shouldReturnHealthStatusUpWithoutAuthentication() {
        var response = restClient.get()
                .uri("/actuator/health")
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("UP");

        // Validar componentes de salud reportados
        if (body.containsKey("components")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsKey("db");
            assertThat(components).containsKey("backupStorage");

            @SuppressWarnings("unchecked")
            Map<String, Object> backupStorage = (Map<String, Object>) components.get("backupStorage");
            assertThat(backupStorage.get("status")).isEqualTo("UP");
            assertThat(backupStorage).containsKey("details");
        }
    }

    @Test
    @DisplayName("BE-39: GET /actuator/info es accesible y describe la aplicación")
    void shouldReturnAppInfo() {
        var response = restClient.get()
                .uri("/actuator/info")
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
    }

    @Test
    @DisplayName("BE-39: Endpoints administrativos de actuator no públicos requieren autenticación")
    void shouldProtectSensitiveActuatorEndpoints() {
        var response = restClient.get()
                .uri("/actuator/beans")
                .retrieve()
                .onStatus(status -> status.value() == 401 || status.value() == 403 || status.value() == 404,
                        (req, resp) -> {
                            // Esperado rechazo por seguridad
                        })
                .toBodilessEntity();

        assertThat(response.getStatusCode().value()).isIn(401, 403, 404);
    }
}
