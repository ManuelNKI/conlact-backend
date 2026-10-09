package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.auth.AuthLoginRequest;
import com.conlact.conlact_backend.dto.auth.AuthLoginResponse;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.repository.ProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String apiKey;
    private final ProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public AuthService(
            @Value("${app.supabase.url}") String supabaseUrl,
            @Value("${app.supabase.anon-key:}") String anonKey,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey,
            ProfileRepository profileRepository
    ) {
        this.supabaseUrl = (supabaseUrl != null && !supabaseUrl.isBlank()) ? supabaseUrl.replaceAll("/+$", "") : "";
        this.apiKey = (anonKey != null && !anonKey.isBlank()) ? anonKey.trim() : (serviceRoleKey != null ? serviceRoleKey.trim() : "");
        this.profileRepository = profileRepository;
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder().build();
    }

    public AuthLoginResponse login(AuthLoginRequest request) {
        String tokenEndpoint = supabaseUrl + "/auth/v1/token?grant_type=password";

        try {
            String responseBody = restClient.post()
                    .uri(tokenEndpoint)
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "email", request.email(),
                            "password", request.password()
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = root.path("access_token").asText();
            String tokenType = root.path("token_type").asText("bearer");
            Long expiresIn = root.has("expires_in") ? root.path("expires_in").asLong() : 3600L;
            String refreshToken = root.has("refresh_token") ? root.path("refresh_token").asText() : null;

            JsonNode userNode = root.path("user");
            String userId = userNode.path("id").asText();
            String email = userNode.path("email").asText(request.email());

            String role = "USER";
            String fullName = null;

            if (userId != null && !userId.isBlank()) {
                try {
                    UUID userUuid = UUID.fromString(userId);
                    Optional<Profile> profileOpt = profileRepository.findById(userUuid);
                    if (profileOpt.isPresent()) {
                        Profile profile = profileOpt.get();
                        role = profile.getRole() != null ? profile.getRole().name() : "USER";
                        fullName = profile.getFullName();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("ID de usuario no es UUID válido: {}", userId);
                }
            }

            return new AuthLoginResponse(
                    accessToken,
                    tokenType,
                    expiresIn,
                    refreshToken,
                    userId,
                    email,
                    role,
                    fullName
            );
        } catch (RestClientResponseException ex) {
            log.error("Error al autenticar en Supabase Auth: HTTP {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            String errorMsg = "Credenciales incorrectas o error en el proveedor de autenticación.";
            try {
                JsonNode errJson = objectMapper.readTree(ex.getResponseBodyAsString());
                if (errJson.has("error_description")) {
                    errorMsg = errJson.path("error_description").asText();
                } else if (errJson.has("msg")) {
                    errorMsg = errJson.path("msg").asText();
                } else if (errJson.has("message")) {
                    errorMsg = errJson.path("message").asText();
                }
            } catch (Exception ignored) {
            }
            throw new ResponseStatusException(HttpStatus.valueOf(ex.getStatusCode().value()), errorMsg);
        } catch (Exception ex) {
            log.error("Excepción inesperada en proceso de login: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al autenticar usuario: " + ex.getMessage());
        }
    }
}
