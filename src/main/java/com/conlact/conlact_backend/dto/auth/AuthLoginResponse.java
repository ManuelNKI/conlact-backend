package com.conlact.conlact_backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de inicio de sesión con token JWT")
public record AuthLoginResponse(
        @Schema(description = "Token de acceso JWT para incluir en el header Authorization Bearer")
        @JsonProperty("access_token")
        String accessToken,

        @Schema(description = "Tipo de token", example = "bearer")
        @JsonProperty("token_type")
        String tokenType,

        @Schema(description = "Tiempo de expiración en segundos", example = "3600")
        @JsonProperty("expires_in")
        Long expiresIn,

        @Schema(description = "Token de refresco")
        @JsonProperty("refresh_token")
        String refreshToken,

        @Schema(description = "Identificador único (UUID) del usuario en Supabase")
        @JsonProperty("user_id")
        String userId,

        @Schema(description = "Correo electrónico del usuario")
        @JsonProperty("email")
        String email,

        @Schema(description = "Rol asignado en el perfil de la base de datos (ej. admin)", example = "admin")
        @JsonProperty("role")
        String role,

        @Schema(description = "Nombre completo del usuario")
        @JsonProperty("full_name")
        String fullName
) {
}
