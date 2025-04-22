package com.gimnasio.apigateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class MiembroResumenFilter extends AbstractGatewayFilterFactory<MiembroResumenFilter.Config> {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public MiembroResumenFilter(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Extraer ID del miembro de la URL
            String path = exchange.getRequest().getURI().getPath();
            String miembroId = path.substring(path.lastIndexOf('/') + 1);

            log.info("Generando resumen para miembro ID: {}", miembroId);

            // Extraer el token de autorización para reenviarlo a los microservicios
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            WebClient webClient = webClientBuilder.build();

            // 1. Obtener información básica del miembro
            Mono<JsonNode> miembroInfo = obtenerInformacionMiembro(webClient, config.getBaseUri(), miembroId, authHeader);

            // 2. Obtener clases del miembro
            Mono<JsonNode> clasesInfo = obtenerClasesMiembro(webClient, config.getClasesUri(), miembroId, authHeader);

            // 3. Obtener pagos del miembro
            Mono<JsonNode> pagosInfo = obtenerPagosMiembro(webClient, config.getPagosUri(), miembroId, authHeader);

            // 4. Combinar toda la información
            return Mono.zip(miembroInfo, clasesInfo, pagosInfo)
                    .map(tuple -> {
                        JsonNode miembro = tuple.getT1();
                        JsonNode clases = tuple.getT2();
                        JsonNode pagos = tuple.getT3();

                        // Crear un objeto JSON combinado
                        ObjectNode resumen = objectMapper.createObjectNode();
                        resumen.set("miembro", miembro);
                        resumen.set("clases", clases);
                        resumen.set("pagos", pagos);

                        // Añadir información de resumen adicional
                        ObjectNode stats = objectMapper.createObjectNode();
                        if (clases.isArray()) {
                            stats.put("totalClases", clases.size());
                        }
                        if (pagos.isArray()) {
                            int totalPagos = 0;
                            ArrayNode pagosArray = (ArrayNode) pagos;
                            for (JsonNode pago : pagosArray) {
                                totalPagos += pago.path("monto").asInt(0);
                            }
                            stats.put("totalPagos", totalPagos);
                        }
                        resumen.set("estadisticas", stats);

                        return resumen;
                    })
                    .flatMap(resumen -> {
                        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                        return exchange.getResponse().writeWith(
                                Mono.just(exchange.getResponse().bufferFactory().wrap(resumen.toString().getBytes()))
                        );
                    })
                    .onErrorResume(error -> {
                        log.error("Error al generar resumen del miembro", error);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "Error al generar resumen: " + error.getMessage());
                    });
        };
    }

    private Mono<JsonNode> obtenerInformacionMiembro(WebClient webClient, String baseUri, String miembroId, String authHeader) {
        return webClient
                .get()
                .uri(baseUri + "/miembros/" + miembroId)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(error -> {
                    log.error("Error al obtener información del miembro {}: {}", miembroId, error.getMessage());
                    return Mono.just(objectMapper.createObjectNode()
                            .put("id", miembroId)
                            .put("error", "No se pudo obtener información del miembro"));
                });
    }

    private Mono<JsonNode> obtenerClasesMiembro(WebClient webClient, String clasesUri, String miembroId, String authHeader) {
        return webClient
                .get()
                .uri(clasesUri + "/clases/miembro/" + miembroId)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(error -> {
                    log.error("Error al obtener clases del miembro {}: {}", miembroId, error.getMessage());
                    return Mono.just(objectMapper.createArrayNode());
                });
    }

    private Mono<JsonNode> obtenerPagosMiembro(WebClient webClient, String pagosUri, String miembroId, String authHeader) {
        return webClient
                .get()
                .uri(pagosUri + "/pagos/miembro/" + miembroId)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(error -> {
                    log.error("Error al obtener pagos del miembro {}: {}", miembroId, error.getMessage());
                    return Mono.just(objectMapper.createArrayNode());
                });
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("baseUri", "clasesUri", "pagosUri");
    }

    @Data
    public static class Config {
        private String baseUri;
        private String clasesUri;
        private String pagosUri;
    }
}