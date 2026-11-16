package com.conlact.conlact_backend.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @JsonProperty("full_name")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
        String fullName,

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "Ingresa un correo electrónico válido")
        @Size(max = 254, message = "El correo electrónico no puede superar los 254 caracteres")
        String email,

        @NotBlank(message = "El rol es obligatorio")
        @Pattern(regexp = "admin|editor", message = "El rol debe ser admin o editor")
        String role,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 128, message = "La contraseña debe tener entre 8 y 128 caracteres")
        String password,

        @NotBlank(message = "La confirmación de contraseña es obligatoria")
        String confirmation
) {}
