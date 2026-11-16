package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.auth.EmailChangeActionRequest;
import com.conlact.conlact_backend.dto.auth.PasswordRecoveryRequest;
import com.conlact.conlact_backend.dto.auth.SessionResponse;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.security.UserPrincipal;
import com.conlact.conlact_backend.service.AuthIdentityService;
import com.conlact.conlact_backend.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;

    @Mock
    private AuthIdentityService authIdentityService;

    @InjectMocks
    private AuthController authController;

    private final UUID authenticatedUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new UserPrincipal(authenticatedUserId, "auth@conlact.ec", "Heydi", "admin");
                    }
                })
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    @DisplayName("Recuperar Password: POST action=request genera código OTP de 6 dígitos")
    void testRecuperarPassword_Request() throws Exception {
        PasswordRecoveryRequest request = new PasswordRecoveryRequest("request", "heydi@conlact.ec", null, null, null, null);

        when(authIdentityService.requestPasswordReset("heydi@conlact.ec")).thenReturn(Map.of(
                "success", true,
                "message", "Si existe una cuenta con ese correo, recibirás un código de recuperación.",
                "requestId", "req-xyz-123",
                "expiresIn", 600,
                "resendAfter", 60
        ));

        mockMvc.perform(post("/api/auth/recuperar-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-xyz-123"));
    }

    @Test
    @DisplayName("Recuperar Password: POST action=verify emite cookie conlact_password_reset")
    void testRecuperarPassword_Verify() throws Exception {
        PasswordRecoveryRequest request = new PasswordRecoveryRequest("verify", null, "req-xyz-123", "482910", null, null);

        when(authIdentityService.verifyResetCode("req-xyz-123", "482910")).thenReturn("token-reset-abc");

        mockMvc.perform(post("/api/auth/recuperar-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().value("conlact_password_reset", "token-reset-abc"))
                .andExpect(cookie().httpOnly("conlact_password_reset", true))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Recuperar Password: POST action=reset consume autorización y limpia cookie")
    void testRecuperarPassword_Reset() throws Exception {
        PasswordRecoveryRequest request = new PasswordRecoveryRequest("reset", null, "req-xyz-123", null, "NuevaClave1!", "NuevaClave1!");

        doNothing().when(authIdentityService).finishPasswordReset(eq("token-reset-abc"), eq("NuevaClave1!"), eq("NuevaClave1!"));

        mockMvc.perform(post("/api/auth/recuperar-password")
                        .cookie(new Cookie("conlact_password_reset", "token-reset-abc"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().maxAge("conlact_password_reset", 0))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Cambiar Correo: POST action=request envía OTP al nuevo correo")
    void testCambiarCorreo_Request() throws Exception {
        EmailChangeActionRequest request = new EmailChangeActionRequest("request", "nuevo@conlact.ec", "ClaveActual1!", null, null);

        when(authIdentityService.requestEmailChange(eq(authenticatedUserId), eq("nuevo@conlact.ec"), eq("ClaveActual1!")))
                .thenReturn(Map.of("success", true, "requestId", "req-email-123"));

        mockMvc.perform(post("/api/auth/cambiar-correo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-email-123"));
    }

    @Test
    @DisplayName("Cambiar Correo: POST action=verify actualiza correo")
    void testCambiarCorreo_Verify() throws Exception {
        EmailChangeActionRequest request = new EmailChangeActionRequest("verify", null, null, "req-email-123", "654321");

        StaffUserResponse updated = StaffUserResponse.builder()
                .id(authenticatedUserId)
                .fullName("Heydi")
                .email("nuevo@conlact.ec")
                .role("admin")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(authIdentityService.verifyEmailChange(eq(authenticatedUserId), eq("req-email-123"), eq("654321")))
                .thenReturn(updated);

        mockMvc.perform(post("/api/auth/cambiar-correo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.email").value("nuevo@conlact.ec"));
    }

    @Test
    @DisplayName("Sesión: GET /api/auth/session retorna perfil con no-store")
    void testGetSession() throws Exception {
        StaffUserResponse user = StaffUserResponse.builder()
                .id(authenticatedUserId)
                .fullName("Heydi")
                .email("auth@conlact.ec")
                .role("admin")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(authIdentityService.getSession(any())).thenReturn(
                new SessionResponse(new SessionResponse.SessionData(user, user))
        );

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.session.user.email").value("auth@conlact.ec"));
    }

    @Test
    @DisplayName("Logout: POST /api/auth/logout retorna 200 y limpia cookie de sesión")
    void testLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("conlact_session", 0))
                .andExpect(jsonPath("$.success").value(true));
    }
}
