package com.gimnasio.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Ejemplo de ruta personalizada para clases populares
                .route("clases-populares", r -> r
                        .path("/api/clases/populares")
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> f
                                .rewritePath("/api/clases/populares", "/clases/populares")
                                .addRequestHeader("X-Source", "api-gateway")
                        )
                        .uri("lb://clases-service")
                )

                // Ejemplo de ruta personalizada para búsqueda de entrenadores por especialidad
                .route("entrenadores-especialidad", r -> r
                        .path("/api/entrenadores/especialidad/**")
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> f
                                .rewritePath("/api/entrenadores/especialidad/(?<especialidad>.*)", "/entrenadores/especialidad/${especialidad}")
                                .addRequestHeader("X-Source", "api-gateway")
                        )
                        .uri("lb://entrenadores-service")
                )

                // Ejemplo de ruta con timeout personalizado para operaciones de pagos
                .route("pagos-procesar", r -> r
                        .path("/api/pagos/procesar/**")
                        .and()
                        .method(HttpMethod.POST)
                        .filters(f -> f
                                .rewritePath("/api/pagos/procesar/(?<id>.*)", "/pagos/procesar/${id}")
                                .addRequestHeader("X-Source", "api-gateway")
                                .circuitBreaker(config -> config
                                        .setName("pagosFallback")
                                        .setFallbackUri("forward:/pagos-fallback")
                                )
                        )
                        .uri("lb://pagos-service")
                )
                .build();
    }
}