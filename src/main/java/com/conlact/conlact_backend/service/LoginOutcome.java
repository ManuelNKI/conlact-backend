package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.auth.AuthLoginResponse;

public record LoginOutcome(AuthLoginResponse response, String sessionToken) {
}
