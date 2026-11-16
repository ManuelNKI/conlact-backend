package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.auth.AuthLoginRequest;
import com.conlact.conlact_backend.dto.auth.AuthLoginResponse;
import com.conlact.conlact_backend.dto.auth.EmailChangeActionRequest;
import com.conlact.conlact_backend.dto.auth.PasswordRecoveryRequest;
import com.conlact.conlact_backend.dto.auth.SessionResponse;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.security.UserPrincipal;
import com.conlact.conlact_backend.service.AuthIdentityService;
import com.conlact.conlact_backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Autenticación e Identidad", description = "Endpoints de autenticación, sesión, recuperación de contraseña y cambio de correo")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String RESET_COOKIE = "conlact_password_reset";
    private final AuthService authService;
    private final AuthIdentityService authIdentityService;

    @Operation(summary = "Iniciar sesión", description = "Autentica con correo y contraseña")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        AuthLoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consultar sesión activa", description = "Retorna el perfil del usuario autenticado o session: null")
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> getSession(@AuthenticationPrincipal UserPrincipal principal) {
        SessionResponse response = authIdentityService.getSession(principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @Operation(summary = "Cerrar sesión", description = "Revoca la sesión y limpia cookies")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse response) {
        ResponseCookie clearSessionCookie = ResponseCookie.from("conlact_session", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearSessionCookie.toString());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("success", true));
    }

    @Operation(summary = "Recuperar o cambiar contraseña (Flujo OTP 6 dígitos)",
            description = "Soporta acciones: request (solicita OTP de 6 dígitos), verify (valida OTP y otorga cookie de reset), reset (cambia contraseña con autorización), cancel")
    @PostMapping("/recuperar-password")
    public ResponseEntity<Map<String, Object>> recuperarPassword(
            @RequestBody PasswordRecoveryRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String action = body.action();
        if (action == null || action.isBlank()) {
            throw new BadRequestException("Acción de recuperación no válida.");
        }

        switch (action) {
            case "request" -> {
                Map<String, Object> result = authIdentityService.requestPasswordReset(body.email());
                clearResetCookie(response);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(result);
            }
            case "verify" -> {
                String resetToken = authIdentityService.verifyResetCode(body.requestId(), body.code());
                setResetCookie(response, resetToken, 300);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(Map.of(
                                "success", true,
                                "message", "Código verificado correctamente.",
                                "resetToken", resetToken
                        ));
            }
            case "reset" -> {
                String token = extractResetToken(request, body.requestId());
                authIdentityService.finishPasswordReset(token, body.password(), body.confirmation());
                clearResetCookie(response);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(Map.of(
                                "success", true,
                                "message", "Contraseña actualizada correctamente. Inicia sesión con tus nuevas credenciales."
                        ));
            }
            case "cancel" -> {
                String token = extractResetToken(request, body.requestId());
                authIdentityService.cancelPasswordReset(token);
                clearResetCookie(response);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(Map.of("success", true));
            }
            default -> throw new BadRequestException("Acción de recuperación no válida: " + action);
        }
    }

    @Operation(summary = "Cambio de correo electrónico verificado (Flujo OTP 6 dígitos)",
            description = "Soporta acciones: request (valida contraseña actual y envía OTP al nuevo correo), verify (valida OTP y actualiza correo)")
    @PostMapping("/cambiar-correo")
    public ResponseEntity<Map<String, Object>> cambiarCorreo(
            @RequestBody EmailChangeActionRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null || principal.getId() == null) {
            throw new BadRequestException("Inicia sesión para cambiar tu correo.");
        }

        String action = body.action();
        if (action == null || action.isBlank()) {
            throw new BadRequestException("Verifica tu nuevo correo con el código de seis dígitos.");
        }

        if ("request".equalsIgnoreCase(action)) {
            Map<String, Object> result = authIdentityService.requestEmailChange(principal.getId(), body.email(), body.currentPassword());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(result);
        } else if ("verify".equalsIgnoreCase(action)) {
            StaffUserResponse updatedUser = authIdentityService.verifyEmailChange(principal.getId(), body.requestId(), body.code());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of(
                            "success", true,
                            "message", "Correo actualizado correctamente.",
                            "user", updatedUser,
                            "profile", updatedUser
                    ));
        }

        throw new BadRequestException("Acción de cambio de correo no válida: " + action);
    }

    private void setResetCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(RESET_COOKIE, token)
                .httpOnly(true)
                .sameSite("Strict")
                .path("/api/auth/recuperar-password")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearResetCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(RESET_COOKIE, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/api/auth/recuperar-password")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractResetToken(HttpServletRequest request, String fallbackToken) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (RESET_COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        return fallbackToken;
    }
}
