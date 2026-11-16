package com.conlact.conlact_backend.security;

import com.conlact.conlact_backend.entity.AuthSession;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.repository.AuthSessionRepository;
import com.conlact.conlact_backend.repository.ProfileRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String SESSION_COOKIE = "conlact_session";

    private final JwtTokenProvider jwtTokenProvider;
    private final ProfileRepository profileRepository;
    private final AuthSessionRepository authSessionRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (authenticateSessionCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);

        if (token != null) {
            Optional<Claims> claimsOpt = jwtTokenProvider.parseAndValidateClaims(token);

            if (claimsOpt.isPresent()) {
                Claims claims = claimsOpt.get();
                Optional<UUID> userIdOpt = jwtTokenProvider.extractUserId(claims);

                if (userIdOpt.isPresent()) {
                    UUID userId = userIdOpt.get();
                    // Buscar perfil en base de datos para verificar que el usuario existe y está activo
                    Optional<Profile> profileOpt = profileRepository.findById(userId);

                    if (profileOpt.isPresent() && Boolean.TRUE.equals(profileOpt.get().getIsActive())) {
                        Profile profile = profileOpt.get();
                        String email = jwtTokenProvider.extractEmail(claims);

                        UserPrincipal userPrincipal = new UserPrincipal(
                                profile.getId(),
                                email,
                                profile.getFullName(),
                                profile.getRole().name()
                        );

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userPrincipal,
                                        null,
                                        userPrincipal.getAuthorities()
                                );

                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.warn("Usuario con id {} no encontrado en profiles o se encuentra inactivo", userId);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean authenticateSessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        String raw = null;
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                raw = cookie.getValue();
                break;
            }
        }
        if (raw == null) {
            return false;
        }
        AuthSession session = authSessionRepository.findByTokenHash(AuthSecrets.sha256(raw)).orElse(null);
        if (session == null || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(OffsetDateTime.now())) {
            return false;
        }
        Optional<Profile> profileOpt = profileRepository.findById(session.getUserId());
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsActive()) || profileOpt.get().getRole() == null) {
            return false;
        }
        Profile profile = profileOpt.get();
        UserPrincipal userPrincipal = new UserPrincipal(
                profile.getId(),
                profile.getEmail(),
                profile.getFullName(),
                profile.getRole().name()
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }
}
