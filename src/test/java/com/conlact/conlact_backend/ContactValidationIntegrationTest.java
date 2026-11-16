package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContactValidationIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("Debe rechazar mensaje de contacto cuando el consentimiento de privacidad no es aceptado")
    void shouldRejectMessageWhenPrivacyNotAccepted() throws Exception {
        Map<String, Object> body = Map.of(
                "nombre", "Comprador Curioso",
                "email", "comprador@dominio.ec",
                "asunto", "Consulta sobre distribución",
                "mensaje", "¿Hacen envíos a Guayaquil?",
                "privacy_accepted", false
        );

        ResponseEntity<String> response = request(HttpMethod.POST, "/api/contacto", body, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Debe absorber silenciosamente spam de bots cuando el campo trampa honeypot viene lleno")
    void shouldRejectSilentlyOrHandleSpamWhenHoneypotFilled() throws Exception {
        Map<String, Object> botBody = Map.of(
                "nombre", "Spam Bot",
                "email", "bot@spammer.org",
                "asunto", "Oferta millonaria no solicitada",
                "mensaje", "Haga clic aquí para ganar dinero rápido",
                "privacy_accepted", true,
                "hp_field", "https://spamlink.ru"
        );

        ResponseEntity<String> response = request(HttpMethod.POST, "/api/contacto", botBody, null);
        // Responde 201 simulado sin guardar en BD para engañar al bot
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verificar que en base de datos NO se haya insertado el mensaje spam
        Integer count = jdbc.queryForObject("SELECT count(*) FROM public.contact_messages WHERE email = 'bot@spammer.org'", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Debe persistir y responder correctamente cuando los datos y consentimiento son válidos")
    void shouldPersistAndNotifyWhenValid() throws Exception {
        Map<String, Object> validBody = Map.of(
                "nombre", "Ana Belén Torres",
                "email", "ana.torres@empresa.ec",
                "telefono", "0991234567",
                "asunto", "Solicitud de catálogo mayorista",
                "mensaje", "Requerimos cotización de quesos curados para cadena hotelera.",
                "privacy_accepted", true
        );

        ResponseEntity<String> response = request(HttpMethod.POST, "/api/contacto", validBody, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = json(response);
        assertThat(json.get("id")).isNotNull();
        assertThat(json.get("status").asText()).isEqualTo("received");

        // El mensaje completo NO debe exponerse en la respuesta pública
        assertThat(json.has("mensaje")).isFalse();
        assertThat(json.has("email")).isFalse();
    }
}
