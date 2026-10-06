package com.conlact.conlact_backend.dto.contact;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactMessageCreateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        @JsonProperty("nombre")
        String name,

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato del correo electrónico no es válido")
        @Size(max = 255, message = "El correo electrónico no puede superar los 255 caracteres")
        @JsonProperty("email")
        String email,

        @Size(max = 50, message = "El teléfono no puede superar los 50 caracteres")
        @JsonProperty("telefono")
        String phone,

        @NotBlank(message = "El asunto es obligatorio")
        @Size(max = 200, message = "El asunto no puede superar los 200 caracteres")
        @JsonProperty("asunto")
        String subject,

        @NotBlank(message = "El mensaje es obligatorio")
        @Size(max = 5000, message = "El mensaje no puede superar los 5000 caracteres")
        @JsonProperty("mensaje")
        String message,

        @JsonProperty("privacy_accepted")
        Boolean privacyAccepted
) {
}
