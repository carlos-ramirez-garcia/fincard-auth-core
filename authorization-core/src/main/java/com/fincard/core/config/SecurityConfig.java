package com.fincard.core.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * El API entera valida JWT (OAuth2 resource server, HS256 simétrico).
 * Solo la salud de apps y el Swagger quedan públicos.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",     // docker compose lo sondea
            "/api/v1/dev/**",
            "/swagger-ui/**",          // UI de la documentación
            "/swagger-ui.html",
            "/v3/api-docs/**",         // JSON de la spec OpenAPI
            "/error"                   // path interno de Spring (si el error page)
    };

    @Value("${fincard.security.jwt.secret}")
    private String jwtSecret;          // la firma HMAC — Confirmación por ENV/secret manager

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                                 // Stateless/JSON, sin reformular
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())                         // TODO el resto exige JWT
                .oauth2ResourceServer(oauth2 -> oauth2.jwt());

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        var key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();                    // valida firma + exp + nbf
    }
}
