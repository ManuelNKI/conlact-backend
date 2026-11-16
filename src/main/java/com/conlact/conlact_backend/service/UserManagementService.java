package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.user.CreateUserRequest;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.conlact.conlact_backend.dto.user.UpdateUserRequest;
import com.conlact.conlact_backend.dto.user.UserListResponse;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.UserRole;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UserManagementService {

    private final ProfileRepository profileRepository;
    private final RestClient restClient;
    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final ObjectMapper objectMapper;

    public UserManagementService(
            ProfileRepository profileRepository,
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey
    ) {
        this.profileRepository = profileRepository;
        this.supabaseUrl = (supabaseUrl != null && !supabaseUrl.isBlank()) ? supabaseUrl.replaceAll("/+$", "") : "";
        this.serviceRoleKey = (serviceRoleKey != null && !serviceRoleKey.isBlank()) ? serviceRoleKey.trim() : "";
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder().build();
    }

    @Transactional(readOnly = true)
    public UserListResponse listUsers() {
        List<StaffUserResponse> users = profileRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(StaffUserResponse::fromEntity)
                .toList();
        return new UserListResponse(users);
    }

    @Transactional
    public StaffUserResponse createUser(CreateUserRequest request) {
        String cleanName = request.fullName().trim();
        String cleanEmail = request.email().trim().toLowerCase();

        if (profileRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ConflictException("Ese correo ya está asociado a otra cuenta.");
        }

        if (!request.password().equals(request.confirmation())) {
            throw new BadRequestException("Las contraseñas no coinciden.");
        }

        validatePasswordPolicy(request.password());

        UserRole role;
        try {
            role = UserRole.valueOf(request.role().toLowerCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("El rol debe ser admin o editor.");
        }

        UUID userId = null;

        // Si tenemos clave de servicio Supabase configurada, crear el usuario en Supabase Auth
        if (!supabaseUrl.isBlank() && !serviceRoleKey.isBlank() && !serviceRoleKey.startsWith("super-secret") && !serviceRoleKey.startsWith("test-")) {
            try {
                String responseBody = restClient.post()
                        .uri(supabaseUrl + "/auth/v1/admin/users")
                        .header("apikey", serviceRoleKey)
                        .header("Authorization", "Bearer " + serviceRoleKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "email", cleanEmail,
                                "password", request.password(),
                                "email_confirm", true,
                                "user_metadata", Map.of("full_name", cleanName)
                        ))
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(responseBody);
                String idStr = root.path("id").asText();
                if (idStr != null && !idStr.isBlank()) {
                    userId = UUID.fromString(idStr);
                }
            } catch (RestClientResponseException ex) {
                log.warn("Error al aprovisionar usuario en Supabase Auth: HTTP {} - {}",
                        ex.getStatusCode(), ex.getResponseBodyAsString());
                if (ex.getStatusCode().value() == 422 || ex.getStatusCode().value() == 409) {
                    throw new ConflictException("Ese correo ya está registrado en el proveedor de autenticación.");
                }
            } catch (Exception ex) {
                log.warn("Excepción al comunicarse con Supabase Auth: {}", ex.getMessage());
            }
        }

        if (userId == null) {
            userId = UUID.randomUUID();
        }

        Profile profile = Profile.builder()
                .id(userId)
                .fullName(cleanName)
                .email(cleanEmail)
                .role(role)
                .isActive(true)
                .build();

        Profile saved = profileRepository.save(profile);
        log.info("Usuario del equipo creado exitosamente: ID {}, email {}, rol {}", saved.getId(), cleanEmail, role);

        return StaffUserResponse.fromEntity(saved);
    }

    @Transactional
    public StaffUserResponse updateUser(UUID id, UpdateUserRequest request, UUID actorId) {
        if (request.fullName() == null && request.role() == null && request.isActive() == null) {
            throw new BadRequestException("Solo puedes actualizar nombre, rol y estado. El correo se verifica desde Mi cuenta.");
        }

        Profile target = profileRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario del equipo no existe."));

        String newName = request.fullName() != null ? request.fullName().trim() : target.getFullName();
        if (newName.length() < 2 || newName.length() > 150) {
            throw new BadRequestException("El nombre debe tener entre 2 y 150 caracteres.");
        }

        UserRole newRole = target.getRole();
        if (request.role() != null) {
            try {
                newRole = UserRole.valueOf(request.role().toLowerCase());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("El rol debe ser admin o editor.");
            }
        }

        boolean newActive = request.isActive() != null ? request.isActive() : Boolean.TRUE.equals(target.getIsActive());

        // Reglas de protección de la propia cuenta y del último admin
        protectAccountAndLastAdmin(actorId, target, newRole, newActive, false);

        target.setFullName(newName);
        target.setRole(newRole);
        target.setIsActive(newActive);

        Profile updated = profileRepository.save(target);
        log.info("Usuario del equipo {} actualizado por actor {}: rol={}, activo={}",
                id, actorId, newRole, newActive);

        // TODO (coordinación con Heydi): Invocar revocación de sesiones/retos al mutar rol o estado

        return StaffUserResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteUser(UUID id, UUID actorId) {
        Profile target = profileRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario del equipo no existe."));

        protectAccountAndLastAdmin(actorId, target, target.getRole(), Boolean.TRUE.equals(target.getIsActive()), true);

        profileRepository.delete(target);
        log.info("Usuario del equipo {} eliminado por actor {}", id, actorId);

        // Intentar eliminar también de Supabase Auth si está configurado
        if (!supabaseUrl.isBlank() && !serviceRoleKey.isBlank() && !serviceRoleKey.startsWith("super-secret") && !serviceRoleKey.startsWith("test-")) {
            try {
                restClient.delete()
                        .uri(supabaseUrl + "/auth/v1/admin/users/" + id)
                        .header("apikey", serviceRoleKey)
                        .header("Authorization", "Bearer " + serviceRoleKey)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ex) {
                log.warn("No se pudo eliminar el usuario {} de Supabase Auth: {}", id, ex.getMessage());
            }
        }

        // TODO (coordinación con Heydi): Invocar revocación de sesiones y retos
    }

    private void protectAccountAndLastAdmin(UUID actorId, Profile target, UserRole newRole, boolean newActive, boolean deleting) {
        // No auto-eliminar, no auto-desactivar, no auto-degradar de admin
        if (actorId != null && actorId.equals(target.getId()) && (deleting || !newActive || newRole != UserRole.admin)) {
            throw new BadRequestException("No puedes eliminar, desactivar o quitar el rol admin de tu propia cuenta.");
        }

        // Proteger que siempre quede al menos un administrador activo
        if (target.getRole() == UserRole.admin && Boolean.TRUE.equals(target.getIsActive()) && (deleting || !newActive || newRole != UserRole.admin)) {
            long activeAdminCount = profileRepository.countByRoleAndIsActiveTrue(UserRole.admin);
            if (activeAdminCount <= 1) {
                throw new ConflictException("Debe permanecer al menos un administrador activo.");
            }
        }
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new BadRequestException("La contraseña debe tener entre 8 y 128 caracteres.");
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BadRequestException("La contraseña debe contener al menos una letra y un dígito.");
        }
    }
}
