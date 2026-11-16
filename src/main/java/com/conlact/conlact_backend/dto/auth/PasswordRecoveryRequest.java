package com.conlact.conlact_backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordRecoveryRequest(
        String action,
        String email,
        @JsonProperty("requestId") String requestId,
        String code,
        String password,
        String confirmation
) {}
