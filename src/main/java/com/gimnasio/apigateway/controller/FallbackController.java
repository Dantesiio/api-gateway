package com.gimnasio.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
public class FallbackController {

    @GetMapping("/fallback")
    public Mono<ResponseEntity<Map<String, Object>>> defaultFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("mensaje", "Lo sentimos, el servicio no está disponible en este momento. Por favor, inténtelo más tarde.");
        response.put("estado", "error");

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }

    @GetMapping("/miembros-fallback")
    public Mono<ResponseEntity<Map<String, Object>>> miembrosFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("mensaje", "El servicio de miembros no está disponible en este momento. Por favor, inténtelo más tarde.");
        response.put("estado", "error");

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }

    @GetMapping("/clases-fallback")
    public Mono<ResponseEntity<Map<String, Object>>> clasesFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("mensaje", "El servicio de clases no está disponible en este momento. Por favor, inténtelo más tarde.");
        response.put("estado", "error");

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }

    @PostMapping("/pagos-fallback")
    public Mono<ResponseEntity<Map<String, Object>>> pagosFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("mensaje", "El servicio de pagos no está disponible en este momento. Por favor, inténtelo más tarde.");
        response.put("estado", "error");
        response.put("transaccion", "fallida");

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }
}