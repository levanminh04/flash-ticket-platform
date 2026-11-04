package com.flashticket.ticket_service;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TicketSecurityTests.ProtectedEndpoints.class)
class TicketSecurityTests {
  private static final String ISSUER = "https://issuer.test/realms/ticket";
  private static final String ORDER = "/api/v1/orders/00000000-0000-0000-0000-000000000001";
  private static final RSAKey KEY;
  private static final HttpServer JWKS;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("ticket-test").generate();
      JWKS = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      JWKS.createContext(
          "/jwks",
          exchange -> {
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
              output.write(body);
            }
          });
      JWKS.start();
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @AfterAll
  static void stopJwks() {
    JWKS.stop(0);
  }

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
    properties.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    properties.add(
        "spring.security.oauth2.resourceserver.jwt.audiences[0]", () -> "flash-ticket-api");
  }

  @Autowired MockMvc mvc;

  private String token(String role) throws Exception {
    return token(
        KEY, ISSUER, "flash-ticket-api", 300, "subject-test", Map.of("roles", List.of(role)));
  }

  private String token(
      RSAKey signingKey,
      String issuer,
      String audience,
      long expiresIn,
      String subject,
      Object realm)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issueTime(Date.from(Instant.now().minusSeconds(30)))
            .expirationTime(Date.from(Instant.now().plusSeconds(expiresIn)))
            .claim("realm_access", realm)
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  @Test
  void anonymousBuyerRequestGetsJsonBearerChallenge() throws Exception {
    mvc.perform(get(ORDER + "/tickets").accept("text/html"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void buyerCanReachOwnApiSurfaceWithoutACsrfSession() throws Exception {
    String bearer = "Bearer " + token("BUYER");
    mvc.perform(get(ORDER + "/tickets").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/tickets/ticket-id/qr").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
    mvc.perform(post(ORDER + "/ticket-deliveries").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ORGANIZER", "ADMIN", "INTERNAL_SERVICE"})
  void otherRolesCannotDownloadBuyerQr(String role) throws Exception {
    mvc.perform(
            get("/api/v1/tickets/ticket-id/qr")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(role)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void onlyOrganizerCanReachCheckIn() throws Exception {
    mvc.perform(
            post("/api/v1/events/event-id/check-ins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ORGANIZER")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/events/event-id/check-ins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void internalRoleCanProbeAndReadHealthButBuyerCannot() throws Exception {
    String bearer = "Bearer " + token("INTERNAL_SERVICE");
    mvc.perform(
            get("/internal/v1/orders/order-id/issuance").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
    mvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
    mvc.perform(
            get("/internal/v1/orders/order-id/issuance")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownRoutesAndUnexpectedHttpMethodsAreDenied() throws Exception {
    String bearer = "Bearer " + token("INTERNAL_SERVICE");
    mvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isForbidden());
    mvc.perform(get("/internal/v1/unknown").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(ORDER + "/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"issuer", "audience", "expired", "signature"})
  void invalidJwtIsRejected(String reason) throws Exception {
    String jwt =
        token(
            reason.equals("signature") ? new RSAKeyGenerator(2048).generate() : KEY,
            reason.equals("issuer") ? "https://other.test" : ISSUER,
            reason.equals("audience") ? "other-api" : "flash-ticket-api",
            reason.equals("expired") ? -600 : 300,
            "subject-test",
            Map.of("roles", List.of("BUYER")));
    mvc.perform(get(ORDER + "/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
  }

  @Test
  void malformedRolesAndMissingSubjectGrantNoAccess() throws Exception {
    for (Object realm :
        List.of(Map.of(), Map.of("roles", "BUYER"), Map.of("roles", List.of("BUYER", 1)))) {
      String jwt = token(KEY, ISSUER, "flash-ticket-api", 300, "subject-test", realm);
      mvc.perform(get(ORDER + "/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
          .andExpect(status().isForbidden());
    }
    String jwt =
        token(KEY, ISSUER, "flash-ticket-api", 300, null, Map.of("roles", List.of("BUYER")));
    mvc.perform(get(ORDER + "/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
        .andExpect(status().isForbidden());
  }

  // Test endpoints prove the filter rules; they do not implement Ticket business logic/ownership.
  @RestController
  static class ProtectedEndpoints {
    @GetMapping({
      "/api/v1/orders/{id}/tickets",
      "/api/v1/tickets/{id}/qr",
      "/internal/v1/orders/{id}/issuance",
      "/internal/v1/unknown",
      "/actuator/env"
    })
    Map<String, Boolean> read() {
      return Map.of("reached", true);
    }

    @PostMapping({
      "/api/v1/orders/{id}/ticket-deliveries",
      "/api/v1/events/{id}/check-ins",
      "/api/v1/orders/{id}/tickets"
    })
    Map<String, Boolean> write() {
      return Map.of("reached", true);
    }
  }
}
