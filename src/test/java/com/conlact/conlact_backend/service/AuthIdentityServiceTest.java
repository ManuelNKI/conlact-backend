package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.auth.EmailChangeResult;
import com.conlact.conlact_backend.repository.AuthSessionRepository;
import com.conlact.conlact_backend.security.AuthSecrets;
import com.conlact.conlact_backend.email.EmailDeliveryException;
import com.conlact.conlact_backend.email.EmailTemplate;
import com.conlact.conlact_backend.email.IEmailService;
import com.conlact.conlact_backend.entity.EmailChangeChallenge;
import com.conlact.conlact_backend.entity.PasswordResetChallenge;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.UserRole;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.repository.EmailChangeChallengeRepository;
import com.conlact.conlact_backend.repository.PasswordResetChallengeRepository;
import com.conlact.conlact_backend.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthIdentityServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PasswordResetChallengeRepository resetChallengeRepository;

    @Mock
    private EmailChangeChallengeRepository emailChangeChallengeRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private AccountPasswordChecker passwordChecker;

    @Mock
    private AuthRateLimiter rateLimiter;

    @Mock
    private IEmailService emailService;

    private AuthIdentityService authIdentityService;

    @BeforeEach
    void setUp() {
        authIdentityService = new AuthIdentityService(
                profileRepository,
                resetChallengeRepository,
                emailChangeChallengeRepository,
                sessionRepository,
                emailService,
                passwordChecker,
                rateLimiter,
                "",
                ""
        );
    }

    @Test
    @DisplayName("OTP Reset: Solicitud exitosa genera código de 6 dígitos y despacha por SMTP")
    void shouldRequestPasswordResetSuccessfullyWithSixDigitCode() {
        UUID userId = UUID.randomUUID();
        Profile profile = Profile.builder()
                .id(userId)
                .fullName("Heydi Pérez")
                .email("heydi@conlact.ec")
                .role(UserRole.admin)
                .isActive(true)
                .build();

        when(profileRepository.findByEmailIgnoreCase("heydi@conlact.ec")).thenReturn(Optional.of(profile));

        Map<String, Object> result = authIdentityService.requestPasswordReset("heydi@conlact.ec");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("requestId")).isNotNull();
        assertThat(result.get("expiresIn")).isEqualTo(600);
        assertThat(result.get("resendAfter")).isEqualTo(60);

        ArgumentCaptor<PasswordResetChallenge> challengeCaptor = ArgumentCaptor.forClass(PasswordResetChallenge.class);
        verify(resetChallengeRepository).save(challengeCaptor.capture());
        PasswordResetChallenge savedChallenge = challengeCaptor.getValue();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpCode(
                eq("heydi@conlact.ec"),
                eq("Heydi Pérez"),
                codeCaptor.capture(),
                eq(EmailTemplate.PASSWORD_RESET_CODE)
        );
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThat(savedChallenge.getCode()).isEqualTo(AuthSecrets.sha256(codeCaptor.getValue()));
        assertThat(savedChallenge.getEmail()).isEqualTo("heydi@conlact.ec");
        assertThat(savedChallenge.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("OTP Reset: Correo no registrado responde genéricamente sin despachar email")
    void shouldReturnGenericResponseForNonExistentEmail() {
        when(profileRepository.findByEmailIgnoreCase("desconocido@conlact.ec")).thenReturn(Optional.empty());

        Map<String, Object> result = authIdentityService.requestPasswordReset("desconocido@conlact.ec");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("requestId")).isNotNull();
        verify(emailService, never()).sendOtpCode(any(), any(), any(), any());
    }

    @Test
    @DisplayName("OTP Reset: Verificación exitosa de código de 6 dígitos otorga resetToken")
    void shouldVerifyResetCodeSuccessfully() {
        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .requestId("req-123")
                .code(AuthSecrets.sha256("482910"))
                .attempts(0)
                .maxAttempts(5)
                .isVerified(false)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(500))
                .build();

        when(resetChallengeRepository.findByRequestId("req-123")).thenReturn(Optional.of(challenge));

        String token = authIdentityService.verifyResetCode("req-123", "482910");

        assertThat(token).isNotBlank();
        assertThat(challenge.isVerified()).isTrue();
        assertThat(challenge.getResetToken()).isEqualTo(AuthSecrets.sha256(token));
        verify(resetChallengeRepository).save(challenge);
    }

    @Test
    @DisplayName("OTP Reset: Código incorrecto incrementa contador de intentos")
    void shouldIncrementAttemptsAndFailOnInvalidCode() {
        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .requestId("req-123")
                .code(AuthSecrets.sha256("482910"))
                .attempts(0)
                .maxAttempts(5)
                .isVerified(false)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(500))
                .build();

        when(resetChallengeRepository.findByRequestId("req-123")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> authIdentityService.verifyResetCode("req-123", "999999"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("incorrecto");

        assertThat(challenge.getAttempts()).isEqualTo(1);
        verify(resetChallengeRepository).save(challenge);
    }

    @Test
    @DisplayName("OTP Reset: Bloqueo tras 5 intentos fallidos")
    void shouldBlockAfterFiveFailedAttempts() {
        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .requestId("req-123")
                .code(AuthSecrets.sha256("482910"))
                .attempts(4)
                .maxAttempts(5)
                .isVerified(false)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(500))
                .build();

        when(resetChallengeRepository.findByRequestId("req-123")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> authIdentityService.verifyResetCode("req-123", "000000"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("máximo");

        assertThat(challenge.getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("OTP Reset: Restablecer contraseña valida coincidencia y política y consume el reto")
    void shouldFinishPasswordResetSuccessfully() {
        UUID userId = UUID.randomUUID();
        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .resetToken(AuthSecrets.sha256("token-abc"))
                .userId(userId)
                .email("user@conlact.ec")
                .isVerified(true)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(200))
                .build();

        when(resetChallengeRepository.findByResetToken(AuthSecrets.sha256("token-abc"))).thenReturn(Optional.of(challenge));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(Profile.builder()
                .id(userId)
                .fullName("Usuario")
                .email("user@conlact.ec")
                .role(UserRole.admin)
                .isActive(true)
                .build()));

        authIdentityService.finishPasswordReset("token-abc", "NuevaClave123!", "NuevaClave123!");

        assertThat(challenge.isConsumed()).isTrue();
        verify(resetChallengeRepository).save(challenge);
        verify(resetChallengeRepository).deleteByUserId(userId);
    }

    @Test
    @DisplayName("OTP Reset: Rechazar contraseñas que no coinciden o no cumplen política")
    void shouldRejectMismatchedOrWeakPassword() {
        assertThatThrownBy(() -> authIdentityService.finishPasswordReset("token", "Clave123!", "Diferente123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no coinciden");

        assertThatThrownBy(() -> authIdentityService.finishPasswordReset("token", "sololetras", "sololetras"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("al menos una letra y un dígito");
    }

    @Test
    @DisplayName("Cambio de correo: Envía OTP de 6 dígitos al NUEVO correo")
    void shouldRequestEmailChangeAndSendOtpToNewEmail() {
        UUID userId = UUID.randomUUID();
        Profile profile = Profile.builder()
                .id(userId)
                .fullName("Heydi")
                .email("actual@conlact.ec")
                .role(UserRole.editor)
                .isActive(true)
                .build();

        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.existsByEmailIgnoreCase("nuevo@conlact.ec")).thenReturn(false);

        Map<String, Object> result = authIdentityService.requestEmailChange(userId, "nuevo@conlact.ec", "ClaveActual1!");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("requestId")).isNotNull();

        ArgumentCaptor<EmailChangeChallenge> captor = ArgumentCaptor.forClass(EmailChangeChallenge.class);
        verify(emailChangeChallengeRepository).save(captor.capture());
        EmailChangeChallenge saved = captor.getValue();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpCode(
                eq("nuevo@conlact.ec"),
                eq("Heydi"),
                codeCaptor.capture(),
                eq(EmailTemplate.EMAIL_CHANGE_CODE)
        );
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThat(saved.getCode()).isEqualTo(AuthSecrets.sha256(codeCaptor.getValue()));
        assertThat(saved.getNewEmail()).isEqualTo("nuevo@conlact.ec");
    }

    @Test
    @DisplayName("Cambio de correo: Verificar OTP actualiza el correo en la base de datos")
    void shouldVerifyEmailChangeAndCompleteUpdate() {
        UUID userId = UUID.randomUUID();
        EmailChangeChallenge challenge = EmailChangeChallenge.builder()
                .requestId("req-email-1")
                .userId(userId)
                .currentEmail("viejo@conlact.ec")
                .newEmail("destino@conlact.ec")
                .code(AuthSecrets.sha256("654321"))
                .attempts(0)
                .maxAttempts(5)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(300))
                .build();

        Profile profile = Profile.builder()
                .id(userId)
                .fullName("Heydi")
                .email("viejo@conlact.ec")
                .role(UserRole.editor)
                .isActive(true)
                .build();

        when(emailChangeChallengeRepository.findByRequestId("req-email-1")).thenReturn(Optional.of(challenge));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        EmailChangeResult updated = authIdentityService.verifyEmailChange(userId, "req-email-1", "654321");

        assertThat(updated.profile().email()).isEqualTo("destino@conlact.ec");
        assertThat(updated.user().appMetadata().get("role")).isEqualTo("editor");
        assertThat(updated.sessionToken()).isNotBlank();
        assertThat(challenge.isConsumed()).isTrue();
        assertThat(profile.getEmail()).isEqualTo("destino@conlact.ec");
        verify(profileRepository).save(profile);
        verify(emailChangeChallengeRepository).deleteByUserId(userId);
    }

    @Test
    @DisplayName("OTP Reset: un código expirado no se puede verificar")
    void shouldRejectExpiredResetCode() {
        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .requestId("req-exp")
                .code(AuthSecrets.sha256("482910"))
                .attempts(0)
                .maxAttempts(5)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().minusSeconds(1))
                .build();
        when(resetChallengeRepository.findByRequestId("req-exp")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> authIdentityService.verifyResetCode("req-exp", "482910"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    @DisplayName("OTP Reset: un código ya consumido no se reutiliza")
    void shouldRejectConsumedResetCode() {
        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .requestId("req-used")
                .code(AuthSecrets.sha256("482910"))
                .attempts(0)
                .maxAttempts(5)
                .isConsumed(true)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();
        when(resetChallengeRepository.findByRequestId("req-used")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> authIdentityService.verifyResetCode("req-used", "482910"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    @DisplayName("OTP Reset: si SMTP falla, el reto no queda guardado como utilizable")
    void shouldDiscardChallengeWhenSmtpFails() {
        Profile profile = Profile.builder()
                .id(UUID.randomUUID())
                .fullName("Heydi")
                .email("heydi@conlact.ec")
                .role(UserRole.admin)
                .isActive(true)
                .build();
        when(profileRepository.findByEmailIgnoreCase("heydi@conlact.ec")).thenReturn(Optional.of(profile));
        doThrow(new EmailDeliveryException("smtp down")).when(emailService)
                .sendOtpCode(any(), any(), any(), any());

        assertThatThrownBy(() -> authIdentityService.requestPasswordReset("heydi@conlact.ec"))
                .isInstanceOf(EmailDeliveryException.class);

        verify(resetChallengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("OTP Reset: el reenvío respeta la espera y luego rota el código")
    void shouldEnforceResendCooldownAndThenRotateCode() {
        UUID userId = UUID.randomUUID();
        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .requestId("req-resend")
                .userId(userId)
                .email("heydi@conlact.ec")
                .code("111111")
                .attempts(1)
                .maxAttempts(5)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .resendAfter(OffsetDateTime.now().plusSeconds(30))
                .build();
        Profile profile = Profile.builder().id(userId).fullName("Heydi").email("heydi@conlact.ec")
                .role(UserRole.admin).isActive(true).build();
        when(resetChallengeRepository.findByRequestId("req-resend")).thenReturn(Optional.of(challenge));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> authIdentityService.resendPasswordReset("req-resend"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Espera");
        verify(emailService, never()).sendOtpCode(any(), any(), any(), any());

        challenge.setResendAfter(OffsetDateTime.now().minusSeconds(1));
        Map<String, Object> resent = authIdentityService.resendPasswordReset("req-resend");

        assertThat(resent.get("requestId")).isEqualTo("req-resend");
        assertThat(challenge.getAttempts()).isEqualTo(1);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpCode(eq("heydi@conlact.ec"), eq("Heydi"), codeCaptor.capture(), eq(EmailTemplate.PASSWORD_RESET_CODE));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThat(challenge.getCode()).isEqualTo(AuthSecrets.sha256(codeCaptor.getValue()));
    }

    @Test
    @DisplayName("OTP Reset: restablecer no reactiva una cuenta deshabilitada")
    void shouldNotReactivateDisabledAccountOnPasswordReset() {
        UUID userId = UUID.randomUUID();
        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .resetToken(AuthSecrets.sha256("token-off"))
                .userId(userId)
                .email("off@conlact.ec")
                .isVerified(true)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusMinutes(2))
                .build();
        when(resetChallengeRepository.findByResetToken(AuthSecrets.sha256("token-off"))).thenReturn(Optional.of(challenge));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(Profile.builder()
                .id(userId).fullName("Inactivo").email("off@conlact.ec").role(UserRole.editor).isActive(false).build()));

        assertThatThrownBy(() -> authIdentityService.finishPasswordReset("token-off", "NuevaClave123!", "NuevaClave123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no está activa");
        assertThat(challenge.isConsumed()).isFalse();
        verify(resetChallengeRepository, never()).deleteByUserId(userId);
    }
}
