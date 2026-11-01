package com.flashticket.booking_service.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.config.SecurityConfig;
import com.flashticket.booking_service.web.error.BookingErrorController;
import com.flashticket.booking_service.web.error.CorrelationFilter;
import com.flashticket.booking_service.web.error.GlobalExceptionHandler;
import com.flashticket.booking_service.web.error.ProblemResponses;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@WebMvcTest(SecurityConfigTests.ProbeController.class)
@Import({
  SecurityConfig.class,
  SecurityConfigTests.ProbeController.class,
  BookingJsonConfig.class,
  ProblemResponses.class,
  CorrelationFilter.class,
  GlobalExceptionHandler.class,
  BookingErrorController.class
})
class SecurityConfigTests {
  static final RSAKey KEY;
  static final HttpServer JWK_SERVER;
  static final String ISSUER = "https://issuer.example.test/realms/booking";

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
      JWK_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      byte[] keys = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
      JWK_SERVER.createContext(
          "/jwks",
          exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, keys.length);
            try (var output = exchange.getResponseBody()) {
              output.write(keys);
            }
          });
      JWK_SERVER.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
    properties.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + JWK_SERVER.getAddress().getPort() + "/jwks");
    properties.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> "flash-ticket-api");
  }

  @AfterAll
  static void close() {
    JWK_SERVER.stop(0);
  }

  @Autowired MockMvc mvc;

  @RestController
  static class ProbeController {
    @RequestMapping({
      "/api/v1/events/{id}/availability",
      "/actuator/health",
      "/actuator/health/readiness",
      "/actuator/info",
      "/internal/v1/probe",
      "/api/v1/orders",
      "/api/v1/orders/{id}",
      "/other"
    })
    String probe() {
      return "allowed";
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/events/event/availability",
        "/actuator/health",
        "/actuator/health/readiness"
      })
  void publicRoutesAreAccessibleWithoutToken(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string("allowed"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/orders", "/api/v1/orders/one", "/other", "/internal/v1/probe"})
  void protectedRoutesChallengeAnonymousRequests(String path) throws Exception {
    var result =
        mvc.perform(get(path))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.violations").isArray())
            .andExpect(header().exists("X-Correlation-Id"))
            .andExpect(header().exists("X-Request-Id"))
            .andReturn();
    assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "BUYER",
        "ADMIN",
        "ORGANIZER",
        "unknown",
        "internal_service",
        "ROLE_INTERNAL_SERVICE"
      })
  void internalAndOtherActuatorRoutesDenyEveryBusinessRole(String role) throws Exception {
    for (String path : List.of("/internal/v1/probe", "/actuator/info")) {
      mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(role)))
          .andExpect(status().isForbidden());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "ORGANIZER", "unknown", "buyer", "INTERNAL_SERVICE"})
  void ordersRequireBuyerNotMerelyAnotherValidRole(String role) throws Exception {
    mvc.perform(
            get("/api/v1/orders/one").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(role)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void buyerCanUseOrderRoutesWithoutCsrfOrSession() throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
    mvc.perform(
            get("/api/v1/orders/one").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isOk());
  }

  @Test
  void internalServiceCanUseInternalRoutesWithoutCsrfOrSession() throws Exception {
    String bearer = "Bearer " + token("INTERNAL_SERVICE");
    var result =
        mvc.perform(post("/internal/v1/probe").header(HttpHeaders.AUTHORIZATION, bearer))
            .andExpect(status().isOk())
            .andExpect(content().string("allowed"))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
    mvc.perform(get("/internal/v1/probe").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
    mvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isForbidden());
  }

  @Test
  void authenticatedFallbackDoesNotRequireBusinessRole() throws Exception {
    mvc.perform(get("/other").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("unknown")))
        .andExpect(status().isOk());
  }

  @Test
  void availabilityIsPublicOnlyForGet() throws Exception {
    mvc.perform(post("/api/v1/events/event/availability")).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired", "future", "issuer", "audience", "signature", "malformed"})
  void bearerValidationRejectsInvalidTokens(String problem) throws Exception {
    String value = problem.equals("malformed") ? "not-a-jwt" : signed(problem, "INTERNAL_SERVICE");
    for (String path : List.of("/other", "/internal/v1/probe")) {
      mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + value))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
          .andExpect(
              content()
                  .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(value))))
          .andExpect(
              header()
                  .string(
                      "WWW-Authenticate",
                      org.hamcrest.Matchers.not(
                          org.hamcrest.Matchers.containsString("error_description"))));
    }
  }

  @Test
  void errorDispatchIsAllowedButDirectErrorRequestRemainsProtected() throws Exception {
    mvc.perform(get("/error"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            get("/error")
                .with(
                    request -> {
                      request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                      request.setAttribute(
                          jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404);
                      return request;
                    }))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
  }

  private static String token(String role) throws Exception {
    return signed(role);
  }

  private static String signed(String variant) throws Exception {
    return signed(variant, variant);
  }

  private static String signed(String variant, String role) throws Exception {
    Instant now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .subject(role.equals("INTERNAL_SERVICE") ? "service-account-internal-service" : "buyer")
            .issuer(variant.equals("issuer") ? "https://wrong.test" : ISSUER)
            .audience(variant.equals("audience") ? "wrong-api" : "flash-ticket-api")
            .issueTime(Date.from(now.minusSeconds(600)))
            .notBeforeTime(
                Date.from(variant.equals("future") ? now.plusSeconds(600) : now.minusSeconds(600)))
            .expirationTime(
                Date.from(variant.equals("expired") ? now.minusSeconds(600) : now.plusSeconds(600)))
            .claim("realm_access", Map.of("roles", List.of(role)))
            .build();
    SignedJWT jwt =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
    jwt.sign(
        new RSASSASigner(variant.equals("signature") ? new RSAKeyGenerator(2048).generate() : KEY));
    return jwt.serialize();
  }
}
