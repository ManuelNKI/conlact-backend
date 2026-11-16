package com.conlact.conlact_backend.dto.auth;

import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Inicio de sesión. La cookie HttpOnly conlact_session es el transporte; no se devuelve el token.")
public record AuthLoginResponse(
        boolean success,
        SessionAccount user,
        StaffUserResponse profile
) {
}
