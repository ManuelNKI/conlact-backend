package com.conlact.conlact_backend.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @JsonProperty("full_name")
        @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
        String fullName,

        String role,

        @JsonProperty("is_active")
        Boolean isActive
) {}
