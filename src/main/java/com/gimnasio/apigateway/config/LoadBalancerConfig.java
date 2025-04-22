package com.gimnasio.apigateway.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@LoadBalancerClients({
        @LoadBalancerClient(name = "miembros-service"),
        @LoadBalancerClient(name = "clases-service"),
        @LoadBalancerClient(name = "entrenadores-service"),
        @LoadBalancerClient(name = "equipos-service"),
        @LoadBalancerClient(name = "pagos-service")
})
public class LoadBalancerConfig {
    // La configuración básica se maneja mediante anotaciones
    // Spring Cloud LoadBalancer utilizará la estrategia por defecto (Round Robin)
}