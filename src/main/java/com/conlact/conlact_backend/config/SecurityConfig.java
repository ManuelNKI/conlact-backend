package com.conlact.conlact_backend.config;

import com.conlact.conlact_backend.security.JwtAuthenticationEntryPoint;
import com.conlact.conlact_backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // La sesión de identidad viaja en cookie SameSite. El filtro de origen cubre los POST de /api/auth.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .authorizeHttpRequests(auth -> auth
                        // Swagger y OpenAPI
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Spring Boot Actuator Health & Monitoreo Público (BE-39)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()

                        // Endpoints de métricas avanzadas de Actuator protegidos para administradores
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Error dispatch de Spring Boot
                        .requestMatchers("/error").permitAll()

                        // Preflight requests CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Autenticación pública. No hay registro abierto: cualquier otra ruta de /api/auth exige sesión.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout", "/api/auth/recuperar-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cambiar-correo").authenticated()

                        // Endpoints públicos del frontend (Tienda, Asociaciones, Recetas, Turismo, Testimonios, Páginas CMS, Capacitación)
                        .requestMatchers(HttpMethod.GET, "/api/associations/**", "/api/asociaciones/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/categorias/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/productos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/recipes/**", "/api/recetas/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tourism/**", "/api/turismo/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/testimonials/**", "/api/testimonios/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shipping-zones/**", "/api/zonas-envio/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/paginas/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/capacitacion/**").permitAll()

                        // Checkout y Formulario de Contacto (Públicos para clientes finales)
                        .requestMatchers(HttpMethod.POST, "/api/orders", "/api/pedidos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/**", "/api/pedidos/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact", "/api/contacto").permitAll()

                        // Webhooks de pasarelas de pago (PayPhone u otros)
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()

                        // Usuarios y confirmación de pagos quedan solo en admin. El editor entra al CMS ya anotado.
                        .requestMatchers("/api/admin/usuarios/**", "/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/pedidos/**", "/api/admin/orders/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/admin/productos/**",
                                "/api/admin/products/**",
                                "/api/admin/variantes/**",
                                "/api/admin/variants/**",
                                "/api/admin/asociaciones/**",
                                "/api/admin/associations/**"
                        ).hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(
                                "/api/admin/paginas/**",
                                "/api/admin/contenidos/**",
                                "/api/admin/secciones/**",
                                "/api/admin/recetas/**",
                                "/api/admin/recipes/**",
                                "/api/admin/turismo/**",
                                "/api/admin/tourism/**"
                        ).hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Cualquier otra petición no especificada requiere autenticación
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
