package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Slf4j
@Service
public class SupabaseAccountPasswordChecker implements AccountPasswordChecker {

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String anonKey;

    public SupabaseAccountPasswordChecker(
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.anon-key:}") String anonKey
    ) {
        this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.replaceAll("/+$", "");
        this.anonKey = anonKey == null ? "" : anonKey.trim();
        this.restClient = RestClient.builder().build();
    }

    @Override
    public void assertMatches(String email, String password) {
        if (!matches(email, password)) {
            throw new BadRequestException("La contraseña actual es incorrecta.");
        }
    }

    @Override
    public boolean matches(String email, String password) {
        if (!configured() || email == null || email.isBlank() || password == null || password.isEmpty() || password.length() > 128) {
            return false;
        }
        try {
            restClient.post()
                    .uri(supabaseUrl + "/auth/v1/token?grant_type=password")
                    .header("apikey", anonKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", password))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                return false;
            }
            log.warn("No se pudo comprobar la contraseña en el proveedor de identidad. HTTP {}", ex.getStatusCode().value());
            throw new BadRequestException("No se pudo verificar la contraseña actual.");
        } catch (BadRequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("No se pudo comprobar la contraseña en el proveedor de identidad.");
            throw new BadRequestException("No se pudo verificar la contraseña actual.");
        }
    }

    private boolean configured() {
        return !supabaseUrl.isBlank() && !anonKey.isBlank() && !anonKey.startsWith("test-");
    }
}
