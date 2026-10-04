package com.conlact.conlact_backend.dto.contact;

import com.conlact.conlact_backend.entity.ContactMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record AdminContactMessageResponse(
        UUID id,
        @JsonProperty("nombre") String name,
        String email,
        @JsonProperty("telefono") String phone,
        @JsonProperty("asunto") String subject,
        @JsonProperty("mensaje") String message,
        @JsonProperty("privacy_accepted") Boolean privacyAccepted,
        @JsonProperty("is_resolved") Boolean isResolved,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
    public static AdminContactMessageResponse fromEntity(ContactMessage entity) {
        return AdminContactMessageResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .subject(entity.getSubject())
                .message(entity.getMessage())
                .privacyAccepted(entity.getPrivacyAccepted())
                .isResolved(entity.getIsResolved())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
