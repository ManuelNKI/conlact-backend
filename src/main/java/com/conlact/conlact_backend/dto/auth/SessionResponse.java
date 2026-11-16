package com.conlact.conlact_backend.dto.auth;

import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionResponse(
        @JsonProperty("session") SessionData session
) {
    public record SessionData(
            SessionAccount user,
            StaffUserResponse profile
    ) {}
}
