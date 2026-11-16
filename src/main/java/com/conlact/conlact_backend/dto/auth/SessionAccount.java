package com.conlact.conlact_backend.dto.auth;

import com.conlact.conlact_backend.entity.Profile;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Forma de usuario que ya consume AuthContext: id, correo y rol dentro de app_metadata.
 */
public record SessionAccount(
        UUID id,
        String email,
        String aud,
        String role,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("app_metadata") Map<String, String> appMetadata,
        @JsonProperty("user_metadata") Map<String, String> userMetadata
) {
    public static SessionAccount from(Profile profile) {
        String roleName = profile.getRole() == null ? "admin" : profile.getRole().name();
        String fullName = profile.getFullName() == null ? "" : profile.getFullName();
        return new SessionAccount(
                profile.getId(),
                profile.getEmail(),
                "authenticated",
                "authenticated",
                profile.getCreatedAt(),
                Map.of("role", roleName),
                Map.of("full_name", fullName)
        );
    }
}
