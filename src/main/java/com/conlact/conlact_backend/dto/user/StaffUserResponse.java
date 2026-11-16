package com.conlact.conlact_backend.dto.user;

import com.conlact.conlact_backend.entity.Profile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record StaffUserResponse(
        UUID id,
        @JsonProperty("full_name") String fullName,
        String email,
        String role,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
    public static StaffUserResponse fromEntity(Profile profile) {
        return StaffUserResponse.builder()
                .id(profile.getId())
                .fullName(profile.getFullName())
                .email(profile.getEmail())
                .role(profile.getRole() != null ? profile.getRole().name() : "admin")
                .isActive(Boolean.TRUE.equals(profile.getIsActive()))
                .createdAt(profile.getCreatedAt())
                .build();
    }
}
