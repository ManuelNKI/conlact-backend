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
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .authorizeHttpRequests(auth -> auth
                        // Error dispatch de Spring Boot
                        .requestMatchers("/error").permitAll()

                        // Preflight requests CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Endpoints públicos del frontend (Tienda, Asociaciones, Recetas, Turismo, Testimonios)
                        .requestMatchers(HttpMethod.GET, "/api/associations/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/recipes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tourism/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/testimonials/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shipping-zones/**").permitAll()

                        // Checkout y Formulario de Contacto (Públicos para clientes finales)
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/track/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()

                        // Webhooks de pasarelas de pago (PayPhone u otros)
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()

                        // Endpoints Administrativos y Backoffice (Requieren ROL ADMIN vía Supabase JWT)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Cualquier otra petición no especificada requiere autenticación
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
