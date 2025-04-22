# Implementación del Patrón API Gateway para Microservicios de Gimnasio

## Introducción

Este documento explica detalladamente la implementación de un API Gateway utilizando Spring Boot y Spring Cloud Gateway para un sistema de microservicios de gimnasio. Se han seguido los lineamientos del taller, adaptando las soluciones a las necesidades específicas del negocio de gimnasio.

## Estructura del Proyecto

### 1. Configuración Inicial del Proyecto API Gateway

Utilizamos Spring Initializr (https://start.spring.io/) para generar el proyecto con las siguientes características:

- **Tipo de proyecto**: Maven
- **Lenguaje**: Java
- **Versión de Spring Boot**: 3.2.6 (asegurando compatibilidad con Spring Cloud)
- **Grupo**: com.gimnasio
- **Artefacto**: api-gateway
- **Nombre**: api-gateway
- **Descripción**: API Gateway para sistema de microservicios de gimnasio
- **Versión de Java**: 17

**Dependencias incluidas**:
- Spring Cloud Gateway
- Spring Boot Actuator
- Spring Security
- Spring Cloud LoadBalancer
- Eureka Client
- Spring OAuth2 Resource Server

### 2. Estructura de Archivos

```
api-gateway/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── gimnasio/
│   │   │           └── apigateway/
│   │   │               ├── ApiGatewayApplication.java
│   │   │               ├── config/
│   │   │               │   ├── KeycloakRealmRoleConverter.java
│   │   │               │   ├── SecurityConfig.java
│   │   │               │   ├── RouteConfig.java
│   │   │               │   ├── LoadBalancerConfig.java
│   │   │               │   └── WebClientConfig.java
│   │   │               ├── controller/
│   │   │               │   └── FallbackController.java
│   │   │               └── filter/
│   │   │                   └── MiembroResumenFilter.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
└── pom.xml
```

## Componentes Principales

### 1. Enrutamiento (Paso 2 del Taller)

#### application.yml

El archivo de configuración establece las rutas a los diferentes microservicios:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: miembros-service
          uri: lb://miembros-service
          predicates:
            - Path=/api/miembros/**
          filters:
            - RewritePath=/api/miembros/(?<path>.*), /$\{path}
            
        - id: clases-service
          uri: lb://clases-service
          predicates:
            - Path=/api/clases/**
          filters:
            - RewritePath=/api/clases/(?<path>.*), /$\{path}
            
        # Otras rutas similares para entrenadores, equipos, pagos...
```

#### RouteConfig.java

Complementa la configuración de rutas con lógica adicional:

```java
@Configuration
public class RouteConfig {
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
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
            // Otras rutas personalizadas...
            .build();
    }
}
```

### 2. Seguridad con Keycloak (Paso 3 del Taller)

#### KeycloakRealmRoleConverter.java

Convierte los roles de Keycloak a autoridades de Spring Security:

```java
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        final Map<String, Object> realmAccess = (Map<String, Object>) 
                jwt.getClaims().get("realm_access");
        
        if (realmAccess == null || realmAccess.isEmpty()) {
            return List.of(); // Retorna lista vacía si no hay roles
        }
        
        return ((List<String>) realmAccess.get("roles")).stream()
                .map(roleName -> {
                    if (roleName.startsWith("ROLE_")) {
                        return new SimpleGrantedAuthority(roleName);
                    } else {
                        return new SimpleGrantedAuthority("ROLE_" + roleName);
                    }
                })
                .collect(Collectors.toList());
    }
}
```

#### SecurityConfig.java

Configura la seguridad del API Gateway:

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/api/miembros/**").hasAnyRole("ADMIN", "STAFF", "MIEMBRO")
                .pathMatchers("/api/clases/**").permitAll()
                .pathMatchers("/api/entrenadores/**").hasAnyRole("ADMIN", "STAFF")
                .pathMatchers("/api/equipos/**").hasAnyRole("ADMIN", "STAFF")
                .pathMatchers("/api/pagos/**").hasAnyRole("ADMIN", "STAFF", "MIEMBRO")
                .pathMatchers("/api/resumen/**").hasAnyRole("ADMIN", "STAFF", "MIEMBRO")
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
            
        return http.build();
    }
    
    @Bean
    public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
```

### 3. Balanceo de Carga con Eureka (Paso 4 del Taller)

#### LoadBalancerConfig.java

```java
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
```

En el archivo `application.yml` también configuramos la integración con Eureka:

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

### 4. Filtro de Agregación de Respuestas (Paso 5 del Taller)

#### MiembroResumenFilter.java

Este filtro personalizado agrega información de múltiples servicios:

```java
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

    // Métodos auxiliares para obtener información de cada servicio...
    
    @Data
    public static class Config {
        private String baseUri;
        private String clasesUri;
        private String pagosUri;
    }
}
```

En el archivo `application.yml` configuramos una ruta específica para este filtro:

```yaml
spring:
  cloud:
    gateway:
      routes:
        # ... otras rutas ...
        
        - id: miembro-resumen
          uri: no://op
          predicates:
            - Path=/api/resumen/miembro/**
          filters:
            - name: MiembroResumenFilter
              args:
                baseUri: lb://miembros-service
                clasesUri: lb://clases-service
                pagosUri: lb://pagos-service
```

## Configuración del Servidor Eureka

Para que el sistema funcione correctamente, se implementó un servidor Eureka independiente:

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

Con su configuración en `application.yml`:

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: false
```

## Configuración de Keycloak

Para la autenticación y autorización, se configuró Keycloak con:

1. **Realm**: gimnasio
2. **Roles**:
    - ADMIN: Administradores del gimnasio
    - STAFF: Personal del gimnasio
    - MIEMBRO: Clientes del gimnasio

3. **Cliente OAuth2**:
    - ID: gimnasio-app
    - Access Type: confidential
    - Valid Redirect URIs: http://localhost:8080/*

4. **Usuarios de prueba** con sus respectivos roles:
    - admin_gimnasio (ADMIN)
    - staff_gimnasio (STAFF)
    - miembro_gimnasio (MIEMBRO)

## Escenarios Específicos del Gimnasio

### 1. Dashboard de Miembro

El filtro de agregación se diseñó pensando en proporcionar un dashboard completo para los miembros donde puedan ver:
- Su información personal
- Las clases a las que están inscritos
- El historial de pagos
- Estadísticas como número total de clases asistidas y monto total pagado

Este enfoque mejora significativamente la experiencia del usuario al obtener toda esta información en una sola solicitud.

### 2. Gestión de Clases Populares

Se implementó una ruta específica (`/api/clases/populares`) para permitir a los usuarios ver las clases más demandadas sin necesidad de autenticación, facilitando que nuevos clientes potenciales puedan conocer la oferta del gimnasio.

### 3. Búsqueda de Entrenadores por Especialidad

Se creó una ruta personalizada (`/api/entrenadores/especialidad/{especialidad}`) para permitir a los usuarios buscar entrenadores según su especialidad (yoga, musculación, cardio, etc.).

## Beneficios de la Implementación

### 1. Para los Usuarios Finales

- **Experiencia unificada**: Interacción con un único punto de entrada
- **Respuestas optimizadas**: Agregación de datos para reducir múltiples llamadas
- **Acceso seguro**: Protección adecuada según el tipo de usuario

### 2. Para el Desarrollo y Mantenimiento

- **Desacoplamiento**: Los microservicios pueden evolucionar independientemente
- **Escalabilidad**: Posibilidad de escalar servicios de forma independiente
- **Resiliencia**: Implementación de circuit breakers y fallbacks
- **Seguridad centralizada**: Política de seguridad unificada

## Consideraciones para Futuras Mejoras

1. **Implementar cachés**: Para mejorar el rendimiento de consultas frecuentes
2. **Rate limiting**: Proteger los servicios contra abusos
3. **Monitoreo avanzado**: Implementar métricas detalladas para cada ruta y servicio
4. **Versionado de API**: Facilitar la evolución de la API manteniendo compatibilidad
5. **Filtros de respuesta**: Permitir a los clientes solicitar solo los campos que necesitan

## Conclusión

La implementación del API Gateway para el sistema de microservicios del gimnasio proporciona una solución robusta, segura y escalable que cumple con todos los requisitos del taller. La arquitectura facilita el mantenimiento y la evolución del sistema, mientras que el filtro de agregación y las rutas personalizadas mejoran considerablemente la experiencia del usuario final.

El uso de tecnologías como Spring Cloud Gateway, Eureka y Keycloak garantiza que la solución siga las mejores prácticas actuales en el desarrollo de arquitecturas de microservicios.