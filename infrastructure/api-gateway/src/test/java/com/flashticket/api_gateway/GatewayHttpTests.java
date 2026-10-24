package com.flashticket.api_gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Real HTTP proxy and signed JWTs; only discovery, Redis and downstream services are substitutes.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.import=file:../config-server/src/main/resources/config/api-gateway.yaml",
      "API_GATEWAY_PORT=0",
      "REDIS_HOST=127.0.0.1",
      "REDIS_PORT=1",
      "REDIS_PASSWORD=fixture",
      "KEYCLOAK_AUDIENCE=flash-ticket-api",
      "CORS_ALLOWED_ORIGINS=http://localhost:5173",
      "resilience4j.timelimiter.instances.bookingServiceBreaker.timeoutDuration=1s",
      "resilience4j.circuitbreaker.instances.bookingServiceBreaker.minimumNumberOfCalls=100"
    })
@LoadBalancerClients(defaultConfiguration = GatewayHttpTests.Discovery.class)
class GatewayHttpTests {
  private static final String CORRELATION = "X-Correlation-Id";
  private static final String REQUEST = "X-Request-Id";
  private static final String ORDER_ID = "00000000-0000-4000-8000-000000000001";
  private static final AtomicInteger CALLS = new AtomicInteger();
  private static final RSAKey KEY = key();
  private static final HttpServer BOOKING = backend("booking");
  private static final HttpServer OTHER = backend("other");
  private static final String ISSUER = "http://127.0.0.1:" + BOOKING.getAddress().getPort();

  @LocalServerPort private int port;
  private WebTestClient client;

  @MockitoBean(name = "redisRateLimiter")
  private RedisRateLimiter rateLimiter;

  @DynamicPropertySource
  static void identity(DynamicPropertyRegistry properties) {
    properties.add("KEYCLOAK_ISSUER_URI", () -> ISSUER);
    properties.add("KEYCLOAK_JWK_SET_URI", () -> ISSUER + "/jwks");
  }

  @BeforeEach
  void allowTraffic() {
    CALLS.set(0);
    when(rateLimiter.isAllowed(anyString(), anyString()))
        .thenReturn(Mono.just(new Response(true, Map.of())));
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .responseTimeout(Duration.ofSeconds(5))
            .build();
  }

  @AfterAll
  static void stopBackends() {
    BOOKING.stop(0);
    OTHER.stop(0);
  }

  @Test
  void forwardsPublicAvailabilityAndNormalizesCorrelation() {
    client
        .get()
        .uri("/api/v1/events/" + ORDER_ID + "/availability")
        .header(CORRELATION, ORDER_ID)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(CORRELATION, ORDER_ID)
        .expectHeader()
        .value(REQUEST, UUID::fromString)
        .expectBody()
        .jsonPath("$.service")
        .isEqualTo("booking")
        .jsonPath("$.correlation")
        .isEqualTo(ORDER_ID);
    client
        .get()
        .uri("/api/v1/events/" + ORDER_ID + "/availability")
        .header(CORRELATION, "bad", ORDER_ID)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .value(
            CORRELATION,
            value -> {
              UUID.fromString(value);
              assertNotEquals(ORDER_ID, value);
            });
  }

  @Test
  void routesListAndPaymentStartToBookingWithoutDroppingHeadersOrQuery() {
    String jwt = token("BUYER", "flash-ticket-api");
    client
        .get()
        .uri("/api/v1/orders?cursor=next")
        .headers(headers -> headers.setBearerAuth(jwt))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.service")
        .isEqualTo("booking")
        .jsonPath("$.query")
        .isEqualTo("cursor=next");
    client
        .post()
        .uri("/api/v1/orders/" + ORDER_ID + "/payment-attempts")
        .headers(headers -> headers.setBearerAuth(jwt))
        .header("Idempotency-Key", ORDER_ID)
        .header(CORRELATION, ORDER_ID)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .bodyValue("{\"provider\":\"VNPAY\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.service")
        .isEqualTo("booking")
        .jsonPath("$.authorization")
        .isEqualTo("Bearer " + jwt)
        .jsonPath("$.idempotency")
        .isEqualTo(ORDER_ID)
        .jsonPath("$.body")
        .isEqualTo("{\"provider\":\"VNPAY\"}");
  }

  @Test
  void returnsCorrelated401() {
    client
        .get()
        .uri("/api/v1/orders")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .exists(HttpHeaders.WWW_AUTHENTICATE)
        .expectHeader()
        .value(REQUEST, UUID::fromString)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHENTICATED")
        .jsonPath("$.correlationId")
        .exists()
        .jsonPath("$.violations")
        .isArray();
    assertEquals(0, CALLS.get());
  }

  @Test
  void rejectsTokensForAnotherAudience() {
    client
        .get()
        .uri("/api/v1/orders/" + ORDER_ID)
        .headers(headers -> headers.setBearerAuth(token("BUYER", "another-api")))
        .exchange()
        .expectStatus()
        .isUnauthorized();
    assertEquals(0, CALLS.get());
  }

  @Test
  void requiresBuyerForAllOrderPathsAndBlocksInternalAndFallbackPaths() {
    String organizer = token("ORGANIZER", "flash-ticket-api");
    client
        .get()
        .uri("/api/v1/orders/" + ORDER_ID)
        .headers(headers -> headers.setBearerAuth(organizer))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("ACCESS_DENIED");
    for (String path :
        List.of("/internal/v1/orders/" + ORDER_ID, "/fallback/booking", "/actuator/env")) {
      client
          .get()
          .uri(path)
          .headers(headers -> headers.setBearerAuth(token("BUYER", "flash-ticket-api")))
          .exchange()
          .expectStatus()
          .isForbidden();
    }
    assertEquals(0, CALLS.get());
  }

  @Test
  void preservesDownstreamBusinessProblemAndItsAuthoritativeCorrelation() {
    client
        .get()
        .uri("/api/v1/events/" + ORDER_ID + "/availability?conflict")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .valueEquals(CORRELATION, ORDER_ID)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("SEAT_UNAVAILABLE")
        .jsonPath("$.correlationId")
        .isEqualTo(ORDER_ID);
    assertEquals(1, CALLS.get());
  }

  @Test
  void doesNotRetryDownstream503AndReturnsCorrelatedRateLimitProblem() {
    client
        .get()
        .uri("/api/v1/events/" + ORDER_ID + "/availability?unready")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CONFIG_NOT_READY");
    assertEquals(1, CALLS.get());
    when(rateLimiter.isAllowed(anyString(), anyString()))
        .thenReturn(Mono.just(new Response(false, Map.of("X-RateLimit-Remaining", "0"))));
    client
        .get()
        .uri("/api/v1/events/" + ORDER_ID + "/availability")
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals("X-RateLimit-Remaining", "0")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED")
        .jsonPath("$.correlationId")
        .exists();
  }

  @Test
  void timesOutWithoutRetryAndKeepsFallbackTechnical() {
    client
        .get()
        .uri("/api/v1/events/" + ORDER_ID + "/availability?slow")
        .exchange()
        .expectStatus()
        .isEqualTo(504)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("DEPENDENCY_TIMEOUT")
        .jsonPath("$.correlationId")
        .exists();
    assertEquals(1, CALLS.get());
  }

  @Test
  void returnsCorrelated404AndAllowsBrowserPreflight() {
    client
        .get()
        .uri("/api/v1/no-such-route")
        .headers(headers -> headers.setBearerAuth(token("BUYER", "flash-ticket-api")))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("ROUTE_NOT_FOUND");
    client
        .options()
        .uri("/api/v1/orders")
        .header("Origin", "http://localhost:5173")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "authorization,idempotency-key,x-correlation-id")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
    client
        .get()
        .uri("/api/v1/orders")
        .header("Origin", "http://localhost:5173")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .value(
            "Access-Control-Expose-Headers",
            value -> org.junit.jupiter.api.Assertions.assertTrue(value.contains(REQUEST)));
  }

  private static String token(String role, String audience) {
    var claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject("fixture-buyer")
            .audience(List.of(audience))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(120))
            .claim("realm_access", Map.of("roles", List.of(role)))
            .build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)))
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("fixture").build(), claims))
        .getTokenValue();
  }

  private static RSAKey key() {
    try {
      return new RSAKeyGenerator(2048).keyID("fixture").generate();
    } catch (com.nimbusds.jose.JOSEException error) {
      throw new IllegalStateException(error);
    }
  }

  private static HttpServer backend(String service) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      server.createContext(
          "/",
          request -> {
            String query = request.getRequestURI().getRawQuery();
            String body;
            int status = 200;
            if (request.getRequestURI().getPath().equals("/jwks")) {
              body = new JWKSet(KEY.toPublicJWK()).toString();
            } else {
              CALLS.incrementAndGet();
              if ("slow".equals(query)) {
                try {
                  Thread.sleep(1800);
                } catch (InterruptedException error) {
                  Thread.currentThread().interrupt();
                }
              }
              if ("conflict".equals(query) || "unready".equals(query)) {
                status = "conflict".equals(query) ? 409 : 503;
                request.getResponseHeaders().set(CORRELATION, ORDER_ID);
                body =
                    "{\"code\":\""
                        + (status == 409 ? "SEAT_UNAVAILABLE" : "CONFIG_NOT_READY")
                        + "\",\"message\":\"fixture\",\"correlationId\":\""
                        + ORDER_ID
                        + "\",\"violations\":[]}";
              } else {
                body =
                    new tools.jackson.databind.json.JsonMapper()
                        .writeValueAsString(
                            Map.of(
                                "service",
                                service,
                                "query",
                                query == null ? "" : query,
                                "correlation",
                                String.valueOf(request.getRequestHeaders().getFirst(CORRELATION)),
                                "authorization",
                                String.valueOf(
                                    request.getRequestHeaders().getFirst("Authorization")),
                                "idempotency",
                                String.valueOf(
                                    request.getRequestHeaders().getFirst("Idempotency-Key")),
                                "body",
                                new String(
                                    request.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8)));
              }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            try {
              request.sendResponseHeaders(status, bytes.length);
              request.getResponseBody().write(bytes);
            } finally {
              request.close();
            }
          });
      server.start();
      return server;
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Discovery {
    @Bean
    ServiceInstanceListSupplier fixtureInstances(Environment environment) {
      String serviceId = environment.getProperty("loadbalancer.client.name");
      int port = ("booking-service".equals(serviceId) ? BOOKING : OTHER).getAddress().getPort();
      return new ServiceInstanceListSupplier() {
        public String getServiceId() {
          return serviceId;
        }

        public Flux<List<ServiceInstance>> get() {
          return Flux.just(
              List.of(new DefaultServiceInstance("fixture", serviceId, "127.0.0.1", port, false)));
        }
      };
    }
  }
}
