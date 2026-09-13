package com.conlact.conlact_backend.security;

import com.conlact.conlact_backend.entity.Profile;
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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final ProfileRepository profileRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

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

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }
}
