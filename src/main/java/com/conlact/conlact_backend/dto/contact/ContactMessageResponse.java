package com.conlact.conlact_backend.dto.contact;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ContactMessageResponse(
        UUID id,
        String status,
        String message
) {
    public static ContactMessageResponse received(UUID id) {
        return ContactMessageResponse.builder()
                .id(id)
                .status("received")
                .message("Mensaje recibido correctamente. Un asesor del consorcio se comunicará a la brevedad.")
                .build();
    }
}
