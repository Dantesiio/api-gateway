package com.gimnasio.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // Configuramos la cadena de filtros de seguridad
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // Deshabilitamos CSRF para APIs
                .authorizeExchange(exchanges -> exchanges
                        // Rutas públicas (no requieren autenticación)
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/v3/api-docs/**").permitAll()
                        .pathMatchers("/swagger-ui/**").permitAll()
                        .pathMatchers("/swagger-ui.html").permitAll()
                        // Rutas protegidas con roles específicos
                        .pathMatchers("/api/miembros/**").permitAll()
                        .pathMatchers("/api/clases/**").permitAll() // Acceso público a la información de clases
                        .pathMatchers("/api/entrenadores/**").hasAnyRole("ADMIN", "STAFF")
                        .pathMatchers("/api/equipos/**").hasAnyRole("ADMIN", "STAFF")
                        .pathMatchers("/api/pagos/**").hasAnyRole("ADMIN", "STAFF", "MIEMBRO")
                        .pathMatchers("/api/resumen/**").hasAnyRole("ADMIN", "STAFF", "MIEMBRO")
                        // Cualquier otra ruta requiere autenticación
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .accessDeniedHandler((exchange, denied) ->
                                Mono.fromRunnable(() ->
                                        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN)
                                )
                        )
                        .authenticationEntryPoint((exchange, ex) ->
                                Mono.fromRunnable(() ->
                                        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED)
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new com.gimnasio.apigateway.config.KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}