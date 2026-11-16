package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.auth.EmailChangeResult;
import com.conlact.conlact_backend.dto.auth.SessionAccount;
import com.conlact.conlact_backend.dto.auth.SessionResponse;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.conlact.conlact_backend.email.EmailDeliveryException;
import com.conlact.conlact_backend.email.EmailTemplate;
import com.conlact.conlact_backend.email.IEmailService;
import com.conlact.conlact_backend.entity.AuthSession;
import com.conlact.conlact_backend.entity.EmailChangeChallenge;
import com.conlact.conlact_backend.entity.PasswordResetChallenge;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.RateLimitException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.AuthSessionRepository;
import com.conlact.conlact_backend.repository.EmailChangeChallengeRepository;
import com.conlact.conlact_backend.repository.PasswordResetChallengeRepository;
import com.conlact.conlact_backend.repository.ProfileRepository;
import com.conlact.conlact_backend.security.AuthSecrets;
import com.conlact.conlact_backend.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AuthIdentityService {

    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final ProfileRepository profileRepository;
    private final PasswordResetChallengeRepository resetChallengeRepository;
    private final EmailChangeChallengeRepository emailChangeChallengeRepository;
    private final AuthSessionRepository sessionRepository;
    private final IEmailService emailService;
    private final AccountPasswordChecker passwordChecker;
    private final AuthRateLimiter rateLimiter;
    private final RestClient restClient;
    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final SecureRandom secureRandom;

    public AuthIdentityService(
            ProfileRepository profileRepository,
            PasswordResetChallengeRepository resetChallengeRepository,
            EmailChangeChallengeRepository emailChangeChallengeRepository,
            AuthSessionRepository sessionRepository,
            IEmailService emailService,
            AccountPasswordChecker passwordChecker,
            AuthRateLimiter rateLimiter,
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey
    ) {
        this.profileRepository = profileRepository;
        this.resetChallengeRepository = resetChallengeRepository;
        this.emailChangeChallengeRepository = emailChangeChallengeRepository;
        this.sessionRepository = sessionRepository;
        this.emailService = emailService;
        this.passwordChecker = passwordChecker;
        this.rateLimiter = rateLimiter;
        this.supabaseUrl = supabaseUrl == null || supabaseUrl.isBlank() ? "" : supabaseUrl.replaceAll("/+$", "");
        this.serviceRoleKey = serviceRoleKey == null ? "" : serviceRoleKey.trim();
        this.restClient = RestClient.builder().build();
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public String openSession(Profile profile) {
        String token = generateRandomHex(32);
        sessionRepository.save(AuthSession.builder()
                .tokenHash(AuthSecrets.sha256(token))
                .userId(profile.getId())
                .expiresAt(OffsetDateTime.now().plus(SESSION_TTL))
                .build());
        return token;
    }

    @Transactional
    public void revokeCurrentSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionRepository.deleteByTokenHash(AuthSecrets.sha256(rawToken));
    }

    public Map<String, Object> requestPasswordReset(String email) {
        return requestPasswordReset(email, "local");
    }

    @Transactional
    public Map<String, Object> requestPasswordReset(String email, String clientIp) {
        if (email == null || email.isBlank() || email.trim().length() > 254) {
            throw new BadRequestException("Ingresa un correo electrónico válido.");
        }
        String cleanEmail = email.trim().toLowerCase();
        String client = clientIp == null || clientIp.isBlank() ? "local" : clientIp;
        rateLimiter.hit("recovery-ip", client, 10, Duration.ofMinutes(15));

        Optional<Profile> profileOpt = profileRepository.findByEmailIgnoreCase(cleanEmail);
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsActive())) {
            swallowEmailLimit(cleanEmail);
            return genericResetResponse(generateRandomHex(16), 60);
        }

        Profile profile = profileOpt.get();
        PasswordResetChallenge active = resetChallengeRepository
                .findFirstByEmailAndIsConsumedFalseOrderByCreatedAtDesc(cleanEmail)
                .orElse(null);
        if (active != null && !active.isConsumed() && active.getExpiresAt().isAfter(OffsetDateTime.now())) {
            if (active.getResendAfter() != null && active.getResendAfter().isAfter(OffsetDateTime.now())) {
                long remaining = Duration.between(OffsetDateTime.now(), active.getResendAfter()).toSeconds();
                return genericResetResponse(active.getRequestId(), (int) Math.max(remaining, 1));
            }
            return rotatePasswordReset(active, profile);
        }

        try {
            rateLimiter.hit("recovery-email", cleanEmail, 1, Duration.ofSeconds(60));
        } catch (RateLimitException ex) {
            return genericResetResponse(generateRandomHex(16), ex.retryAfterSeconds());
        }

        String requestId = generateRandomHex(16);
        String code = sixDigitCode();
        emailService.sendOtpCode(cleanEmail, profile.getFullName(), code, EmailTemplate.PASSWORD_RESET_CODE);
        resetChallengeRepository.deleteByEmail(cleanEmail);
        resetChallengeRepository.save(newResetChallenge(requestId, profile, cleanEmail, code));
        log.info("Reto de recuperación despachado para el usuario {}", profile.getId());
        return genericResetResponse(requestId, 60);
    }

    @Transactional
    public Map<String, Object> resendPasswordReset(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BadRequestException("Debes proporcionar el requestId del código vigente.");
        }
        PasswordResetChallenge challenge = resetChallengeRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BadRequestException("Reto de recuperación no válido o expirado."));
        Profile profile = profileRepository.findById(challenge.getUserId())
                .orElseThrow(() -> new BadRequestException("Reto de recuperación no válido o expirado."));
        if (!Boolean.TRUE.equals(profile.getIsActive())) {
            throw new BadRequestException("Reto de recuperación no válido o expirado.");
        }
        return rotatePasswordReset(challenge, profile);
    }

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
        if (!AuthSecrets.matches(code.trim(), challenge.getCode())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            resetChallengeRepository.save(challenge);
            if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
                throw new BadRequestException("Has superado el número máximo de 5 intentos.");
            }
            throw new BadRequestException("Código de recuperación incorrecto.");
        }

        String resetToken = generateRandomHex(16);
        challenge.setVerified(true);
        challenge.setResetToken(AuthSecrets.sha256(resetToken));
        challenge.setExpiresAt(OffsetDateTime.now().plusSeconds(300));
        resetChallengeRepository.save(challenge);
        log.info("Código de recuperación verificado para el reto {}", requestId);
        return resetToken;
    }

    @Transactional
    public void finishPasswordReset(String resetToken, String password, String confirmation) {
        if (resetToken == null || resetToken.isBlank()) {
            throw new BadRequestException("Se requiere una autorización de restablecimiento válida.");
        }
        if (password == null || confirmation == null || !password.equals(confirmation)) {
            throw new BadRequestException("Las contraseñas no coinciden.");
        }
        validatePasswordPolicy(password);

        PasswordResetChallenge challenge = resetChallengeRepository.findByResetToken(AuthSecrets.sha256(resetToken))
                .orElseThrow(() -> new BadRequestException("La autorización de restablecimiento ha expirado o no es válida."));
        if (!challenge.isVerified() || challenge.isConsumed() || challenge.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("La autorización de restablecimiento ha expirado o no es válida.");
        }

        Profile profile = challenge.getUserId() == null ? null : profileRepository.findById(challenge.getUserId()).orElse(null);
        if (profile == null || !Boolean.TRUE.equals(profile.getIsActive())) {
            throw new BadRequestException("La cuenta no está activa. El restablecimiento no la reactiva.");
        }
        if (passwordChecker.matches(profile.getEmail(), password)) {
            throw new BadRequestException("La nueva contraseña debe ser diferente de la actual.");
        }

        updateSupabasePassword(challenge.getUserId(), password);
        challenge.setConsumed(true);
        resetChallengeRepository.save(challenge);
        revokeSessionsAndChallenges(challenge.getUserId());
        log.info("Restablecimiento de contraseña completado para el usuario {}. Se exige un login nuevo.", profile.getId());
    }

    @Transactional
    public void cancelPasswordReset(String resetToken) {
        if (resetToken == null || resetToken.isBlank()) {
            return;
        }
        resetChallengeRepository.findByResetToken(AuthSecrets.sha256(resetToken)).ifPresent(challenge -> {
            challenge.setConsumed(true);
            resetChallengeRepository.save(challenge);
        });
    }

    @Transactional
    public Map<String, Object> requestEmailChange(UUID userId, String newEmail, String currentPassword) {
        if (userId == null) {
            throw new BadRequestException("Inicia sesión para cambiar tu correo.");
        }
        if (newEmail == null || newEmail.isBlank() || newEmail.trim().length() > 254) {
            throw new BadRequestException("Ingresa el nuevo correo electrónico.");
        }
        if (currentPassword == null || currentPassword.isEmpty() || currentPassword.length() > 128) {
            throw new BadRequestException("La contraseña actual es incorrecta.");
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

        rateLimiter.hit("email-password", userId.toString(), 5, Duration.ofMinutes(15));
        passwordChecker.assertMatches(profile.getEmail(), currentPassword);

        EmailChangeChallenge active = emailChangeChallengeRepository
                .findFirstByUserIdAndIsConsumedFalseOrderByCreatedAtDesc(userId)
                .orElse(null);
        if (active != null && !active.isConsumed() && active.getExpiresAt().isAfter(OffsetDateTime.now())
                && active.getResendAfter() != null && active.getResendAfter().isAfter(OffsetDateTime.now())) {
            long remaining = Duration.between(OffsetDateTime.now(), active.getResendAfter()).toSeconds();
            return emailChangeAccepted(active.getRequestId(), (int) Math.max(remaining, 1));
        }

        rateLimiter.hit("email-change-send", userId.toString(), 1, Duration.ofSeconds(60));
        rateLimiter.hit("email-change-target", cleanEmail, 5, Duration.ofHours(1));

        if (active != null && !active.isConsumed() && active.getExpiresAt().isAfter(OffsetDateTime.now())) {
            return rotateEmailChange(active, profile, cleanEmail);
        }

        String requestId = generateRandomHex(16);
        String code = sixDigitCode();
        emailService.sendOtpCode(cleanEmail, profile.getFullName(), code, EmailTemplate.EMAIL_CHANGE_CODE);
        emailChangeChallengeRepository.deleteByUserId(userId);
        emailChangeChallengeRepository.save(newEmailChallenge(requestId, profile, cleanEmail, code));
        log.info("Reto de cambio de correo despachado para el usuario {}", userId);
        return emailChangeAccepted(requestId, 60);
    }

    @Transactional
    public Map<String, Object> resendEmailChange(UUID userId, String requestId) {
        if (userId == null) {
            throw new BadRequestException("Inicia sesión para cambiar tu correo.");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new BadRequestException("Debes proporcionar el requestId del código vigente.");
        }
        EmailChangeChallenge challenge = emailChangeChallengeRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BadRequestException("Reto de verificación no válido o expirado."));
        if (!challenge.getUserId().equals(userId)) {
            throw new BadRequestException("El reto de verificación no corresponde al usuario autenticado.");
        }
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado."));
        if (!challenge.getCurrentEmail().equalsIgnoreCase(profile.getEmail())) {
            throw new BadRequestException("El correo de la cuenta cambió. Solicita un código nuevo.");
        }
        return rotateEmailChange(challenge, profile, challenge.getNewEmail());
    }

    @Transactional
    public EmailChangeResult verifyEmailChange(UUID userId, String requestId, String code) {
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
        if (!AuthSecrets.matches(code.trim(), challenge.getCode())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            emailChangeChallengeRepository.save(challenge);
            if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
                throw new BadRequestException("Has superado el número máximo de 5 intentos.");
            }
            throw new BadRequestException("Código de verificación incorrecto.");
        }

        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado."));
        if (challenge.getCurrentEmail() != null && profile.getEmail() != null
                && !challenge.getCurrentEmail().equalsIgnoreCase(profile.getEmail())) {
            throw new BadRequestException("El correo de la cuenta cambió. Solicita un código nuevo.");
        }
        String newEmail = challenge.getNewEmail();
        if (profileRepository.existsByEmailIgnoreCase(newEmail) && !newEmail.equalsIgnoreCase(profile.getEmail())) {
            throw new ConflictException("Ese correo ya está asociado a otra cuenta.");
        }

        challenge.setConsumed(true);
        emailChangeChallengeRepository.save(challenge);
        profile.setEmail(newEmail);
        Profile updated = profileRepository.save(profile);
        syncSupabaseEmail(userId, newEmail);
        revokeSessionsAndChallenges(userId);
        String sessionToken = openSession(updated);
        StaffUserResponse staffUser = StaffUserResponse.fromEntity(updated);
        log.info("Cambio de correo verificado para el usuario {}", userId);
        return new EmailChangeResult(SessionAccount.from(updated), staffUser, sessionToken);
    }

    @Transactional
    public void revokeSessionsAndChallenges(UUID userId) {
        if (userId == null) {
            return;
        }
        resetChallengeRepository.deleteByUserId(userId);
        emailChangeChallengeRepository.deleteByUserId(userId);
        sessionRepository.deleteByUserId(userId);
        signOutSupabase(userId);
        log.info("Sesiones y retos revocados para el usuario {}", userId);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            return new SessionResponse(null);
        }
        Optional<Profile> profileOpt = profileRepository.findById(principal.getId());
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsActive())) {
            return new SessionResponse(null);
        }
        Profile profile = profileOpt.get();
        return new SessionResponse(new SessionResponse.SessionData(SessionAccount.from(profile), StaffUserResponse.fromEntity(profile)));
    }

    private void swallowEmailLimit(String email) {
        try {
            rateLimiter.hit("recovery-email", email, 1, Duration.ofSeconds(60));
        } catch (RateLimitException ignored) {
            // La respuesta genérica no distingue si la cuenta existe.
        }
    }

    private PasswordResetChallenge newResetChallenge(String requestId, Profile profile, String email, String code) {
        return PasswordResetChallenge.builder()
                .requestId(requestId)
                .userId(profile.getId())
                .email(email)
                .code(AuthSecrets.sha256(code))
                .attempts(0)
                .maxAttempts(5)
                .isVerified(false)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(600))
                .resendAfter(OffsetDateTime.now().plusSeconds(60))
                .build();
    }

    private EmailChangeChallenge newEmailChallenge(String requestId, Profile profile, String newEmail, String code) {
        return EmailChangeChallenge.builder()
                .requestId(requestId)
                .userId(profile.getId())
                .currentEmail(profile.getEmail() == null ? "" : profile.getEmail())
                .newEmail(newEmail)
                .code(AuthSecrets.sha256(code))
                .attempts(0)
                .maxAttempts(5)
                .isConsumed(false)
                .expiresAt(OffsetDateTime.now().plusSeconds(600))
                .resendAfter(OffsetDateTime.now().plusSeconds(60))
                .build();
    }

    private Map<String, Object> genericResetResponse(String requestId, int resendAfter) {
        return Map.of(
                "success", true,
                "message", "Si existe una cuenta con ese correo, recibirás un código de recuperación.",
                "requestId", requestId,
                "expiresIn", 600,
                "resendAfter", resendAfter
        );
    }

    private Map<String, Object> emailChangeAccepted(String requestId, int resendAfter) {
        return Map.of(
                "success", true,
                "message", "Enviamos un código a tu nuevo correo. Verifícalo para completar el cambio.",
                "requestId", requestId,
                "expiresIn", 600,
                "resendAfter", resendAfter
        );
    }

    private Map<String, Object> rotatePasswordReset(PasswordResetChallenge challenge, Profile profile) {
        if (challenge.isConsumed() || !challenge.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("El código de recuperación ha expirado.");
        }
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw new BadRequestException("Has superado el número máximo de intentos permitidos.");
        }
        if (challenge.getResendAfter() != null && challenge.getResendAfter().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Espera antes de solicitar otro código.");
        }
        String code = sixDigitCode();
        emailService.sendOtpCode(challenge.getEmail(), profile.getFullName(), code, EmailTemplate.PASSWORD_RESET_CODE);
        challenge.setCode(AuthSecrets.sha256(code));
        challenge.setVerified(false);
        challenge.setResetToken(null);
        challenge.setExpiresAt(OffsetDateTime.now().plusSeconds(600));
        challenge.setResendAfter(OffsetDateTime.now().plusSeconds(60));
        resetChallengeRepository.save(challenge);
        return genericResetResponse(challenge.getRequestId(), 60);
    }

    private Map<String, Object> rotateEmailChange(EmailChangeChallenge challenge, Profile profile, String newEmail) {
        if (challenge.isConsumed() || !challenge.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("El código de verificación ha expirado.");
        }
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw new BadRequestException("Has superado el número máximo de intentos permitidos.");
        }
        if (challenge.getResendAfter() != null && challenge.getResendAfter().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Espera antes de solicitar otro código.");
        }
        String code = sixDigitCode();
        emailService.sendOtpCode(newEmail, profile.getFullName(), code, EmailTemplate.EMAIL_CHANGE_CODE);
        challenge.setCode(AuthSecrets.sha256(code));
        challenge.setNewEmail(newEmail);
        challenge.setCurrentEmail(profile.getEmail() == null ? "" : profile.getEmail());
        challenge.setExpiresAt(OffsetDateTime.now().plusSeconds(600));
        challenge.setResendAfter(OffsetDateTime.now().plusSeconds(60));
        emailChangeChallengeRepository.save(challenge);
        return emailChangeAccepted(challenge.getRequestId(), 60);
    }

    private void updateSupabasePassword(UUID userId, String password) {
        if (userId == null || !supabaseAdminConfigured()) {
            return;
        }
        try {
            restClient.put()
                    .uri(supabaseUrl + "/auth/v1/admin/users/" + userId)
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("password", password))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new BadRequestException("No se pudo actualizar la contraseña. Solicita un código nuevo.");
        }
    }

    private void syncSupabaseEmail(UUID userId, String newEmail) {
        if (!supabaseAdminConfigured()) {
            return;
        }
        try {
            restClient.put()
                    .uri(supabaseUrl + "/auth/v1/admin/users/" + userId)
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", newEmail, "email_confirm", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new BadRequestException("No se pudo confirmar el correo nuevo. El correo actual se conserva.");
        }
    }

    private void signOutSupabase(UUID userId) {
        if (!supabaseAdminConfigured()) {
            return;
        }
        try {
            restClient.post()
                    .uri(supabaseUrl + "/auth/v1/admin/users/" + userId + "/logout")
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return;
            }
            throw new BadRequestException("No se pudieron revocar las sesiones del usuario.");
        }
    }

    private boolean supabaseAdminConfigured() {
        return !supabaseUrl.isBlank()
                && !serviceRoleKey.isBlank()
                && !serviceRoleKey.startsWith("super-secret")
                && !serviceRoleKey.startsWith("test-");
    }

    private String sixDigitCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String generateRandomHex(int bytesCount) {
        byte[] bytes = new byte[bytesCount];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void validatePasswordPolicy(String password) {
        if (password.length() < 8 || password.length() > 128) {
            throw new BadRequestException("La contraseña debe tener entre 8 y 128 caracteres.");
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BadRequestException("La contraseña debe contener al menos una letra y un dígito.");
        }
    }
}
