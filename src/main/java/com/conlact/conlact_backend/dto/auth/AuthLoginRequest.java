package com.conlact.conlact_backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Petición de inicio de sesión")
public record AuthLoginRequest(
        @Schema(description = "Correo electrónico registrado en Supabase", example = "admin@conlact.local")
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato del correo electrónico no es válido")
        @JsonProperty("email")
        String email,

        @Schema(description = "Contraseña del usuario", example = "MiPasswordSeguro123*")
        @NotBlank(message = "La contraseña es obligatoria")
        @JsonProperty("password")
        String password
) {
}
