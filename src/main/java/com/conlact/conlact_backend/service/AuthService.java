package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.auth.AuthLoginRequest;
import com.conlact.conlact_backend.dto.auth.AuthLoginResponse;
import com.conlact.conlact_backend.dto.auth.SessionAccount;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    static final String INVALID_LOGIN = "Correo electrónico o contraseña incorrectos.";

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String apiKey;
    private final ProfileRepository profileRepository;
    private final AuthIdentityService authIdentityService;
    private final AuthRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public AuthService(
            @Value("${app.supabase.url}") String supabaseUrl,
            @Value("${app.supabase.anon-key:}") String anonKey,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey,
            ProfileRepository profileRepository,
            AuthIdentityService authIdentityService,
            AuthRateLimiter rateLimiter
    ) {
        this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.replaceAll("/+$", "");
        this.apiKey = (anonKey != null && !anonKey.isBlank()) ? anonKey.trim() : (serviceRoleKey == null ? "" : serviceRoleKey.trim());
        this.profileRepository = profileRepository;
        this.authIdentityService = authIdentityService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder().build();
    }

    public LoginOutcome login(AuthLoginRequest request, String clientIp) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        String password = request.password() == null ? "" : request.password();
        String client = clientIp == null || clientIp.isBlank() ? "local" : clientIp;
        rateLimiter.hit("login-ip", client, 30, Duration.ofMinutes(15));
        if (!email.isBlank()) {
            rateLimiter.hit("login-email", email, 10, Duration.ofMinutes(15));
        }
        if (email.isBlank() || email.length() > 254 || !email.contains("@") || password.isEmpty() || password.length() > 128) {
            throw unauthorized();
        }

        try {
            String responseBody = restClient.post()
                    .uri(supabaseUrl + "/auth/v1/token?grant_type=password")
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", password))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String userId = root.path("user").path("id").asText("");
            if (userId.isBlank()) {
                throw unauthorized();
            }
            UUID userUuid;
            try {
                userUuid = UUID.fromString(userId);
            } catch (IllegalArgumentException ex) {
                throw unauthorized();
            }
            Optional<Profile> profileOpt = profileRepository.findById(userUuid);
            if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsActive()) || profileOpt.get().getRole() == null) {
                throw unauthorized();
            }
            Profile profile = profileOpt.get();
            rateLimiter.clear("login-email", email);
            String sessionToken = authIdentityService.openSession(profile);
            AuthLoginResponse body = new AuthLoginResponse(true, SessionAccount.from(profile), StaffUserResponse.fromEntity(profile));
            return new LoginOutcome(body, sessionToken);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Fallo de autenticación en el proveedor de identidad. HTTP {}", ex.getStatusCode().value());
            if (ex.getStatusCode().is4xxClientError()) {
                throw unauthorized();
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo iniciar sesión. Inténtalo de nuevo.");
        } catch (Exception ex) {
            log.error("Error interno al autenticar.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo iniciar sesión.");
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
    }
}
