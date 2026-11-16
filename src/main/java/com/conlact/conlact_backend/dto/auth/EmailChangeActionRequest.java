package com.conlact.conlact_backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmailChangeActionRequest(
        String action,
        String email,
        @JsonProperty("currentPassword") String currentPassword,
        @JsonProperty("requestId") String requestId,
        String code
) {}
