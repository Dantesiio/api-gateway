package com.gimnasio.apigateway.filters;


import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
public class MiembroResumenFilter extends AbstractGatewayFilterFactory<MiembroResumenFilter.Config> {

    private final WebClient.Builder webClientBuilder;

    public MiembroResumenFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("baseUri", "clasesUri", "pagosUri");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String miembroId = getMiembroIdFromPath(exchange);
            if (miembroId == null) {
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }

            return getMiembroData(miembroId, config.baseUri)
                    .flatMap(miembroData -> {
                        return getClasesData(miembroId, config.clasesUri)
                                .flatMap(clasesData -> {
                                    return getPagosData(miembroId, config.pagosUri)
                                            .flatMap(pagosData -> {
                                                // Combinar los datos
                                                Map<String, Object> respuestaAgregada = new HashMap<>();
                                                respuestaAgregada.put("miembro", miembroData);
                                                respuestaAgregada.put("clases", clasesData);
                                                respuestaAgregada.put("pagos", pagosData);

                                                // Modificar la respuesta
                                                return exchange.getResponse().writeWith(
                                                        Mono.just(exchange.getResponse()
                                                                .bufferFactory()
                                                                .wrap(respuestaAgregada.toString().getBytes())));
                                            });
                                });
                    })
                    .onErrorResume(error -> {
                        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                        return exchange.getResponse().setComplete();
                    });
        };
    }

    private String getMiembroIdFromPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        String[] segments = path.split("/");
        if (segments.length < 4) {
            return null;
        }
        return segments[3]; // /api/resumen/miembro/{id}
    }

    private Mono<Map> getMiembroData(String miembroId, String baseUri) {
        return webClientBuilder
                .baseUrl(baseUri)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/miembros/{id}").build(miembroId))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> Mono.just(Map.of("error", "No se pudo obtener datos del miembro")));
    }

    private Mono<List> getClasesData(String miembroId, String clasesUri) {
        return webClientBuilder
                .baseUrl(clasesUri)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/clases/miembro/{id}").build(miembroId))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(List.class)
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private Mono<List> getPagosData(String miembroId, String pagosUri) {
        return webClientBuilder
                .baseUrl(pagosUri)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/pagos/miembro/{id}").build(miembroId))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(List.class)
                .onErrorResume(e -> Mono.just(List.of()));
    }

    public static class Config {
        private String baseUri;
        private String clasesUri;
        private String pagosUri;

        public String getBaseUri() {
            return baseUri;
        }

        public void setBaseUri(String baseUri) {
            this.baseUri = baseUri;
        }

        public String getClasesUri() {
            return clasesUri;
        }

        public void setClasesUri(String clasesUri) {
            this.clasesUri = clasesUri;
        }

        public String getPagosUri() {
            return pagosUri;
        }

        public void setPagosUri(String pagosUri) {
            this.pagosUri = pagosUri;
        }
    }
}