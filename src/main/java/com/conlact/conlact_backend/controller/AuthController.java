package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.auth.AuthLoginRequest;
import com.conlact.conlact_backend.dto.auth.AuthLoginResponse;
import com.conlact.conlact_backend.dto.auth.EmailChangeActionRequest;
import com.conlact.conlact_backend.dto.auth.EmailChangeResult;
import com.conlact.conlact_backend.dto.auth.PasswordRecoveryRequest;
import com.conlact.conlact_backend.dto.auth.SessionResponse;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.security.ClientAddresses;
import com.conlact.conlact_backend.security.UserPrincipal;
import com.conlact.conlact_backend.service.AuthIdentityService;
import com.conlact.conlact_backend.service.AuthService;
import com.conlact.conlact_backend.service.LoginOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Autenticación e Identidad", description = "Endpoints de autenticación, sesión, recuperación de contraseña y cambio de correo")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String SESSION_COOKIE = "conlact_session";
    static final String RESET_COOKIE = "conlact_password_reset";
    static final int SESSION_MAX_AGE = 7 * 24 * 60 * 60;
    static final int RESET_MAX_AGE = 300;

    private final AuthService authService;
    private final AuthIdentityService authIdentityService;

    @Value("${app.auth.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.auth.trusted-proxies:}")
    private String trustedProxies;

    @Operation(summary = "Iniciar sesión", description = "Autentica con correo y contraseña y emite la cookie de sesión")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(
            @Valid @RequestBody AuthLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginOutcome outcome = authService.login(request, ClientAddresses.clientIp(httpRequest, trustedProxies));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, sessionCookie(httpRequest, outcome.sessionToken(), SESSION_MAX_AGE).toString())
                .body(outcome.response());
    }

    @Operation(summary = "Consultar sesión activa", description = "Retorna el perfil del usuario autenticado o session: null")
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> getSession(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(authIdentityService.getSession(principal));
    }

    @Operation(summary = "Cerrar sesión", description = "Revoca la sesión actual y limpia la cookie")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        authIdentityService.revokeCurrentSession(cookieValue(request, SESSION_COOKIE));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(request, "", 0).toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("success", true));
    }

    @Operation(summary = "Recuperar o cambiar contraseña (flujo OTP de 6 dígitos)")
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

        return switch (action) {
            case "request" -> ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.SET_COOKIE, resetCookie(request, "", 0).toString())
                    .body(authIdentityService.requestPasswordReset(body.email(), ClientAddresses.clientIp(request, trustedProxies)));
            case "verify" -> {
                String resetToken = authIdentityService.verifyResetCode(body.requestId(), body.code());
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .header(HttpHeaders.SET_COOKIE, resetCookie(request, resetToken, RESET_MAX_AGE).toString())
                        .body(Map.<String, Object>of("success", true, "message", "Código verificado correctamente."));
            }
            case "resend" -> ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(authIdentityService.resendPasswordReset(body.requestId()));
            case "reset" -> {
                String token = cookieValue(request, RESET_COOKIE);
                if (token == null) {
                    token = body.requestId();
                }
                authIdentityService.finishPasswordReset(token, body.password(), body.confirmation());
                response.addHeader(HttpHeaders.SET_COOKIE, resetCookie(request, "", 0).toString());
                response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(request, "", 0).toString());
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(Map.<String, Object>of(
                                "success", true,
                                "message", "Contraseña actualizada correctamente. Inicia sesión con tus nuevas credenciales."
                        ));
            }
            case "cancel" -> {
                authIdentityService.cancelPasswordReset(cookieValue(request, RESET_COOKIE));
                response.addHeader(HttpHeaders.SET_COOKIE, resetCookie(request, "", 0).toString());
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(Map.<String, Object>of("success", true));
            }
            default -> throw new BadRequestException("Acción de recuperación no válida.");
        };
    }

    @Operation(summary = "Cambio de correo verificado con OTP de 6 dígitos")
    @PostMapping("/cambiar-correo")
    public ResponseEntity<Map<String, Object>> cambiarCorreo(
            @RequestBody EmailChangeActionRequest body,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request
    ) {
        if (principal == null || principal.getId() == null) {
            throw new BadRequestException("Inicia sesión para cambiar tu correo.");
        }
        String action = body.action();
        if (action == null || action.isBlank()) {
            throw new BadRequestException("Verifica tu nuevo correo con el código de seis dígitos.");
        }
        if ("resend".equalsIgnoreCase(action)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(authIdentityService.resendEmailChange(principal.getId(), body.requestId()));
        }
        if ("request".equalsIgnoreCase(action)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(authIdentityService.requestEmailChange(principal.getId(), body.email(), body.currentPassword()));
        }
        if ("verify".equalsIgnoreCase(action)) {
            EmailChangeResult result = authIdentityService.verifyEmailChange(principal.getId(), body.requestId(), body.code());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.SET_COOKIE, sessionCookie(request, result.sessionToken(), SESSION_MAX_AGE).toString())
                    .body(Map.of(
                            "success", true,
                            "message", "Correo actualizado correctamente.",
                            "user", result.user(),
                            "profile", result.profile()
                    ));
        }
        throw new BadRequestException("Acción de cambio de correo no válida.");
    }

    private ResponseCookie sessionCookie(HttpServletRequest request, String token, int maxAgeSeconds) {
        return ResponseCookie.from(SESSION_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure || request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie resetCookie(HttpServletRequest request, String token, int maxAgeSeconds) {
        return ResponseCookie.from(RESET_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure || request.isSecure())
                .sameSite("Strict")
                .path("/api/auth/recuperar-password")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
