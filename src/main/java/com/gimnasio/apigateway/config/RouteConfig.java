package com.gimnasio.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Ruta para el servicio de miembros
                .route("miembros-service", r -> r
                        .path("/api/miembros/**")
                        .filters(f -> f
                                .rewritePath("/api/miembros/(?<segment>.*)", "/${segment}")
                                .addRequestHeader("X-Source", "api-gateway"))
                        .uri("lb://miembros-service"))

                // Ruta para el servicio de clases
                .route("clases-service", r -> r
                        .path("/api/clases/**")
                        .filters(f -> f
                                .rewritePath("/api/clases/(?<segment>.*)", "/${segment}")
                                .addRequestHeader("X-Source", "api-gateway"))
                        .uri("lb://clases-service"))

                // Ruta para el servicio de entrenadores
                .route("entrenadores-service", r -> r
                        .path("/api/entrenadores/**")
                        .filters(f -> f
                                .rewritePath("/api/entrenadores/(?<segment>.*)", "/${segment}")
                                .addRequestHeader("X-Source", "api-gateway"))
                        .uri("lb://entrenadores-service"))

                // Ruta para el servicio de equipos
                .route("equipos-service", r -> r
                        .path("/api/equipos/**")
                        .filters(f -> f
                                .rewritePath("/api/equipos/(?<segment>.*)", "/${segment}")
                                .addRequestHeader("X-Source", "api-gateway"))
                        .uri("lb://equipos-service"))

                // Ruta para el servicio de pagos
                .route("pagos-service", r -> r
                        .path("/api/pagos/**")
                        .filters(f -> f
                                .rewritePath("/api/pagos/(?<segment>.*)", "/${segment}")
                                .addRequestHeader("X-Source", "api-gateway"))
                        .uri("lb://pagos-service"))

                .build();
    }
}