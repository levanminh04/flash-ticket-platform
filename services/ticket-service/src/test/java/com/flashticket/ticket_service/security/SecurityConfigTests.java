package com.flashticket.ticket_service.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.ticket_service.config.SecurityConfig;
import com.flashticket.ticket_service.config.TicketJsonConfig;
import com.flashticket.ticket_service.exception.GlobalExceptionHandler;
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
  TicketJsonConfig.class,
  GlobalExceptionHandler.class,
})
class SecurityConfigTests {
  static final RSAKey KEY;
  static final HttpServer JWK_SERVER;
  static final String ISSUER = "https://issuer.example.test/realms/ticket";

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
  }

  @AfterAll
  static void close() {
    JWK_SERVER.stop(0);
  }

  @Autowired MockMvc mvc;

  @RestController
  static class ProbeController {
    @RequestMapping({
      "/api/v1/orders/{id}/tickets",
      "/api/v1/orders/{id}/ticket-deliveries",
      "/api/v1/tickets/{id}/qr",
      "/api/v1/events/{id}/check-ins",
      "/internal/v1/orders/{id}/issuance",
      "/actuator/health",
      "/actuator/health/readiness",
      "/actuator/info",
      "/other"
    })
    String probe() {
      return "allowed";
    }
  }

  @Test
  void buyerCanReadOwnRouteButMachineCannot() throws Exception {
    mvc.perform(
            get("/api/v1/orders/one/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/orders/one/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("INTERNAL_SERVICE")))
        .andExpect(status().isForbidden())
        .andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\""));
    mvc.perform(
            post("/api/v1/orders/one/ticket-deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void organizerIsRequiredForCheckIn() throws Exception {
    mvc.perform(
            post("/api/v1/events/one/check-ins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ORGANIZER")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/events/one/check-ins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void internalProbeRequiresMachineRole() throws Exception {
    var result =
        mvc.perform(
                get("/internal/v1/orders/one/issuance")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("INTERNAL_SERVICE")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
    mvc.perform(
            get("/internal/v1/orders/one/issuance")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminDoesNotGainBuyerQrAccess() throws Exception {
    for (String role : List.of("ADMIN", "ORGANIZER", "INTERNAL_SERVICE"))
      mvc.perform(
              get("/api/v1/tickets/one/qr")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(role)))
          .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/tickets/one/qr")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isOk());
  }

  @Test
  void wrongMethodAndUnknownPathAreDenied() throws Exception {
    String bearer = "Bearer " + token("BUYER");
    mvc.perform(delete("/api/v1/orders/one/tickets").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isForbidden());
    mvc.perform(get("/other").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired", "future", "issuer", "signature", "malformed"})
  void invalidTokensAreUnauthorized(String problem) throws Exception {
    String value = problem.equals("malformed") ? "not-a-jwt" : signed(problem, "BUYER");
    var result =
        mvc.perform(
                get("/api/v1/orders/one/tickets")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + value))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andReturn();
    assertThat(result.getResponse().getHeader("WWW-Authenticate"))
        .startsWith("Bearer")
        .doesNotContain("error_description");
    assertThat(result.getResponse().getContentAsString()).doesNotContain(value);
  }

  @Test
  void missingTokenIsChallengedWithoutSession() throws Exception {
    var result =
        mvc.perform(get("/api/v1/orders/one/tickets"))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  void errorDispatchKeepsOriginalStatus() throws Exception {
    mvc.perform(
            get("/error")
                .with(
                    request -> {
                      request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                      request.setAttribute(
                          jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404);
                      return request;
                    }))
        .andExpect(status().isNotFound());
    mvc.perform(get("/error")).andExpect(status().isUnauthorized());
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
