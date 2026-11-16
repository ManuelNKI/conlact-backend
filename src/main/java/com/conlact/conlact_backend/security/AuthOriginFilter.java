package com.conlact.conlact_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Rechaza POST de autenticación cuyo Origin no está en la lista CORS.
 * Las peticiones sin Origin (curl, pruebas, BFF servidor) se permiten.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class AuthOriginFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/recuperar-password",
            "/api/auth/cambiar-correo"
    );

    private final List<String> allowedOrigins;

    public AuthOriginFilter(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod()) || !PROTECTED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
            reject(response);
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank() && allowedOrigins.stream().noneMatch(origin::equals)) {
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"code\":\"FORBIDDEN\",\"error\":\"Origen no permitido.\"}");
    }
}
