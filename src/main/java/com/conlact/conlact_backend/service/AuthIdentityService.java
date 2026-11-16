package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.auth.PasswordRecoveryRequest;
import com.conlact.conlact_backend.dto.auth.SessionResponse;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.conlact.conlact_backend.email.EmailTemplate;
import com.conlact.conlact_backend.email.IEmailService;
import com.conlact.conlact_backend.entity.EmailChangeChallenge;
import com.conlact.conlact_backend.entity.PasswordResetChallenge;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.EmailChangeChallengeRepository;
import com.conlact.conlact_backend.repository.PasswordResetChallengeRepository;
import com.conlact.conlact_backend.repository.ProfileRepository;
import com.conlact.conlact_backend.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
public class AuthIdentityService {

    private final ProfileRepository profileRepository;
    private final PasswordResetChallengeRepository resetChallengeRepository;
    private final EmailChangeChallengeRepository emailChangeChallengeRepository;
    private final IEmailService emailService;
    private final RestClient restClient;
    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final String anonKey;
    private final SecureRandom secureRandom;

    public AuthIdentityService(
            ProfileRepository profileRepository,
            PasswordResetChallengeRepository resetChallengeRepository,
            EmailChangeChallengeRepository emailChangeChallengeRepository,
            IEmailService emailService,
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey,
            @Value("${app.supabase.anon-key:}") String anonKey
    ) {
        this.profileRepository = profileRepository;
        this.resetChallengeRepository = resetChallengeRepository;
        this.emailChangeChallengeRepository = emailChangeChallengeRepository;
        this.emailService = emailService;
        this.supabaseUrl = (supabaseUrl != null && !supabaseUrl.isBlank()) ? supabaseUrl.replaceAll("/+$", "") : "";
        this.serviceRoleKey = (serviceRoleKey != null && !serviceRoleKey.isBlank()) ? serviceRoleKey.trim() : "";
        this.anonKey = (anonKey != null && !anonKey.isBlank()) ? anonKey.trim() : "";
        this.restClient = RestClient.builder().build();
        this.secureRandom = new SecureRandom();
    }

    /**
     * Paso 1 de recuperación: Genera y envía código OTP de 6 dígitos.
     */
    @Transactional
    public Map<String, Object> requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Ingresa un correo electrónico válido.");
        }
        String cleanEmail = email.trim().toLowerCase();
        String requestId = generateRandomHex(16);

        Optional<Profile> profileOpt = profileRepository.findByEmailIgnoreCase(cleanEmail);

        // Si la cuenta no existe o está inactiva, responder genéricamente sin enviar correo
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsActive())) {
            log.info("Solicitud de recuperación para correo inexistente o inactivo: {}", cleanEmail);
            return Map.of(
                    "success", true,
                    "message", "Si existe una cuenta con ese correo, recibirás un código de recuperación.",
                    "requestId", requestId,
                    "expiresIn", 600,
                    "resendAfter", 60
            );
        }

        Profile profile = profileOpt.get();

        // Generar código aleatorio criptográficamente seguro de 6 dígitos
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        // Eliminar retos anteriores no verificados de este correo
        resetChallengeRepository.deleteByEmail(cleanEmail);

        PasswordResetChallenge challenge = PasswordResetChallenge.builder()
                .requestId(requestId)
                .userId(profile.getId())
                .email(cleanEmail)
                .code(code)
                .attempts(0)
                .maxAttempts(5)
                .isVerified(false)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(600))
                .resendAfter(OffsetDateTime.now().plusSeconds(60))
                .build();

        resetChallengeRepository.save(challenge);

        // Despachar el código de 6 dígitos mediante el método síncrono que implementó David
        emailService.sendOtpCode(cleanEmail, profile.getFullName(), code, EmailTemplate.PASSWORD_RESET_CODE);

        log.info("Reto de recuperación creado y despachado por SMTP para usuario {}", profile.getId());

        return Map.of(
                "success", true,
                "message", "Si existe una cuenta con ese correo, recibirás un código de recuperación.",
                "requestId", requestId,
                "expiresIn", 600,
                "resendAfter", 60
        );
    }

    /**
     * Paso 2 de recuperación: Verifica código OTP de 6 dígitos y emite token de reseteo.
     */
    @Transactional
    public String verifyResetCode(String requestId, String code) {
        if (requestId == null || requestId.isBlank() || code == null || code.isBlank()) {
            throw new BadRequestException("Debes proporcionar el requestId y el código de 6 dígitos.");
        }

        PasswordResetChallenge challenge = resetChallengeRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BadRequestException("Reto de recuperación no válido o expirado."));

        if (challenge.isConsumed() || challenge.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("El código de recuperación ha expirado.");
        }

        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw new BadRequestException("Has superado el número máximo de intentos permitidos.");
        }

        if (!challenge.getCode().equals(code.trim())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            resetChallengeRepository.save(challenge);
            if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
                throw new BadRequestException("Has superado el número máximo de 5 intentos.");
            }
            throw new BadRequestException("Código de recuperación incorrecto.");
        }

        // Código válido: emitir token de autorización de reseteo con validez de 5 minutos
        String resetToken = generateRandomHex(16);
        challenge.setVerified(true);
        challenge.setResetToken(resetToken);
        challenge.setExpiresAt(OffsetDateTime.now().plusSeconds(300));
        resetChallengeRepository.save(challenge);

        log.info("Código de recuperación verificado con éxito para reto {}", requestId);
        return resetToken;
    }

    /**
     * Paso 3 de recuperación: Cambia la contraseña usando el resetToken verificado.
     */
    @Transactional
    public void finishPasswordReset(String resetToken, String password, String confirmation) {
        if (resetToken == null || resetToken.isBlank()) {
            throw new BadRequestException("Se requiere una autorización de restablecimiento válida.");
        }
        if (password == null || confirmation == null || !password.equals(confirmation)) {
            throw new BadRequestException("Las contraseñas no coinciden.");
        }
        validatePasswordPolicy(password);

        PasswordResetChallenge challenge = resetChallengeRepository.findByResetToken(resetToken)
                .orElseThrow(() -> new BadRequestException("La autorización de restablecimiento ha expirado o no es válida."));

        if (!challenge.isVerified() || challenge.isConsumed() || challenge.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("La autorización de restablecimiento ha expirado o no es válida.");
        }

        challenge.setConsumed(true);
        resetChallengeRepository.save(challenge);

        // Si tenemos Supabase Auth configurado, actualizar la contraseña del usuario
        if (challenge.getUserId() != null && !supabaseUrl.isBlank() && !serviceRoleKey.isBlank() && !serviceRoleKey.startsWith("super-secret") && !serviceRoleKey.startsWith("test-")) {
            try {
                restClient.put()
                        .uri(supabaseUrl + "/auth/v1/admin/users/" + challenge.getUserId())
                        .header("apikey", serviceRoleKey)
                        .header("Authorization", "Bearer " + serviceRoleKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("password", password))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Contraseña actualizada en Supabase Auth para usuario {}", challenge.getUserId());
            } catch (Exception ex) {
                log.warn("No se pudo actualizar contraseña en Supabase Auth: {}", ex.getMessage());
            }
        }

        // Revocar retos anteriores de este usuario
        if (challenge.getUserId() != null) {
            resetChallengeRepository.deleteByUserId(challenge.getUserId());
        }

        log.info("Restablecimiento de contraseña completado exitosamente para correo {}", challenge.getEmail());
    }

    /**
     * Cancela el reto de recuperación si el usuario desiste.
     */
    @Transactional
    public void cancelPasswordReset(String resetToken) {
        if (resetToken != null && !resetToken.isBlank()) {
            resetChallengeRepository.findByResetToken(resetToken).ifPresent(c -> {
                c.setConsumed(true);
                resetChallengeRepository.save(c);
            });
        }
    }

    /**
     * Paso 1 de cambio de correo: Valida contraseña actual y envía OTP al nuevo correo.
     */
    @Transactional
    public Map<String, Object> requestEmailChange(UUID userId, String newEmail, String currentPassword) {
        if (userId == null) {
            throw new BadRequestException("Inicia sesión para cambiar tu correo.");
        }
        if (newEmail == null || newEmail.isBlank()) {
            throw new BadRequestException("Ingresa el nuevo correo electrónico.");
        }
        String cleanEmail = newEmail.trim().toLowerCase();

        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado."));

        if (cleanEmail.equalsIgnoreCase(profile.getEmail())) {
            throw new BadRequestException("El nuevo correo debe ser diferente al actual.");
        }

        if (profileRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ConflictException("Ese correo ya está asociado a otra cuenta.");
        }

        // Si Supabase está disponible, verificar la contraseña actual
        if (!supabaseUrl.isBlank() && !anonKey.isBlank() && !anonKey.startsWith("test-") && profile.getEmail() != null) {
            try {
                restClient.post()
                        .uri(supabaseUrl + "/auth/v1/token?grant_type=password")
                        .header("apikey", anonKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "email", profile.getEmail(),
                                "password", currentPassword
                        ))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException ex) {
                throw new BadRequestException("La contraseña actual es incorrecta.");
            } catch (Exception ex) {
                log.warn("Excepción al validar contraseña actual en Supabase: {}", ex.getMessage());
            }
        }

        String requestId = generateRandomHex(16);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        // Limpiar retos previos de cambio de correo del usuario
        emailChangeChallengeRepository.deleteByUserId(userId);

        EmailChangeChallenge challenge = EmailChangeChallenge.builder()
                .requestId(requestId)
                .userId(userId)
                .currentEmail(profile.getEmail() != null ? profile.getEmail() : "")
                .newEmail(cleanEmail)
                .code(code)
                .attempts(0)
                .maxAttempts(5)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(600))
                .resendAfter(OffsetDateTime.now().plusSeconds(60))
                .build();

        emailChangeChallengeRepository.save(challenge);

        // Despachar el código al NUEVO correo electrónico
        emailService.sendOtpCode(cleanEmail, profile.getFullName(), code, EmailTemplate.EMAIL_CHANGE_CODE);

        log.info("Reto de cambio de correo enviado al nuevo destino {} para usuario {}", cleanEmail, userId);

        return Map.of(
                "success", true,
                "message", "Enviamos un código a tu nuevo correo. Verifícalo para completar el cambio.",
                "requestId", requestId,
                "expiresIn", 600,
                "resendAfter", 60
        );
    }

    /**
     * Paso 2 de cambio de correo: Valida OTP y actualiza el correo en BD y Supabase.
     */
    @Transactional
    public StaffUserResponse verifyEmailChange(UUID userId, String requestId, String code) {
        if (userId == null) {
            throw new BadRequestException("Inicia sesión para verificar el cambio de correo.");
        }
        if (requestId == null || requestId.isBlank() || code == null || code.isBlank()) {
            throw new BadRequestException("Debes proporcionar el requestId y el código de 6 dígitos.");
        }

        EmailChangeChallenge challenge = emailChangeChallengeRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BadRequestException("Reto de verificación no válido o expirado."));

        if (!challenge.getUserId().equals(userId)) {
            throw new BadRequestException("El reto de verificación no corresponde al usuario autenticado.");
        }

        if (challenge.isConsumed() || challenge.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("El código de verificación ha expirado.");
        }

        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw new BadRequestException("Has superado el número máximo de intentos permitidos.");
        }

        if (!challenge.getCode().equals(code.trim())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            emailChangeChallengeRepository.save(challenge);
            if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
                throw new BadRequestException("Has superado el número máximo de 5 intentos.");
            }
            throw new BadRequestException("Código de verificación incorrecto.");
        }

        challenge.setConsumed(true);
        emailChangeChallengeRepository.save(challenge);

        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado."));

        String newEmail = challenge.getNewEmail();
        profile.setEmail(newEmail);
        Profile updated = profileRepository.save(profile);

        // Si Supabase está disponible, sincronizar el nuevo correo
        if (!supabaseUrl.isBlank() && !serviceRoleKey.isBlank() && !serviceRoleKey.startsWith("super-secret") && !serviceRoleKey.startsWith("test-")) {
            try {
                restClient.put()
                        .uri(supabaseUrl + "/auth/v1/admin/users/" + userId)
                        .header("apikey", serviceRoleKey)
                        .header("Authorization", "Bearer " + serviceRoleKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", newEmail, "email_confirm", true))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Correo actualizado en Supabase Auth para usuario {}: {}", userId, newEmail);
            } catch (Exception ex) {
                log.warn("No se pudo actualizar correo en Supabase Auth: {}", ex.getMessage());
            }
        }

        log.info("Cambio de correo verificado y completado para usuario {}: {}", userId, newEmail);
        return StaffUserResponse.fromEntity(updated);
    }

    /**
     * Consulta de sesión de gestión: /api/auth/session
     */
    @Transactional(readOnly = true)
    public SessionResponse getSession(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            return new SessionResponse(null);
        }

        Optional<Profile> profileOpt = profileRepository.findById(principal.getId());
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsActive())) {
            return new SessionResponse(null);
        }

        StaffUserResponse staffUser = StaffUserResponse.fromEntity(profileOpt.get());
        return new SessionResponse(new SessionResponse.SessionData(staffUser, staffUser));
    }

    private String generateRandomHex(int bytesCount) {
        byte[] bytes = new byte[bytesCount];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new BadRequestException("La contraseña debe tener entre 8 y 128 caracteres.");
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BadRequestException("La contraseña debe contener al menos una letra y un dígito.");
        }
    }
}
