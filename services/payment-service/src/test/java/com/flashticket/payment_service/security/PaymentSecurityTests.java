package com.flashticket.payment_service.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.payment_service.finance.repository.*;
import com.flashticket.payment_service.payment.repository.*;
import com.flashticket.payment_service.refund.repository.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@MockitoBean(
    types = {
      OrderPaymentSnapshotRepository.class,
      PaymentAttemptRepository.class,
      ChargeRepository.class,
      PaymentConfirmationRepository.class,
      RefundRepository.class,
      RefundAttemptRepository.class,
      EventRefundRunRepository.class,
      EventRefundItemRepository.class,
      EventPayoutRepository.class,
      EventFinanceSnapshotRepository.class
    })
class PaymentSecurityTests {
  @Autowired MockMvc mvc;
  static RSAKey signingKey;
  static HttpServer issuerServer;
  static String issuer;

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry properties) throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("payment-test").generate();
    issuerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    issuer = "http://127.0.0.1:" + issuerServer.getAddress().getPort();
    issuerServer.createContext(
        "/",
        exchange -> {
          String body =
              exchange.getRequestURI().getPath().equals("/jwks")
                  ? new JWKSet(signingKey.toPublicJWK()).toString()
                  : "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (var stream = exchange.getResponseBody()) {
            stream.write(bytes);
          }
        });
    issuerServer.start();
    properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
  }

  @AfterAll
  static void stopIssuer() {
    if (issuerServer != null) issuerServer.stop(0);
  }

  private static String signedToken(String role, String scenario) throws JOSEException {
    Instant now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(scenario.equals("issuer") ? "https://wrong.example.test" : issuer)
            .subject("buyer-test")
            .issueTime(Date.from(now.minusSeconds(10)))
            .notBeforeTime(
                Date.from(scenario.equals("future") ? now.plusSeconds(3600) : now.minusSeconds(10)))
            .expirationTime(
                Date.from(
                    scenario.equals("expired") ? now.minusSeconds(120) : now.plusSeconds(300)))
            .claim("realm_access", Map.of("roles", List.of(role)))
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
    jwt.sign(
        new RSASSASigner(
            scenario.equals("signature") ? new RSAKeyGenerator(2048).generate() : signingKey));
    return jwt.serialize();
  }

  @Test
  void rejectsMissingAndInvalidJwtWithProblem() throws Exception {
    String path = "/api/v1/orders/one/payment";
    mvc.perform(get(path))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
    for (String scenario : List.of("expired", "future", "issuer", "signature")) {
      mvc.perform(get(path).header("Authorization", "Bearer " + signedToken("BUYER", scenario)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
          .andExpect(header().exists("X-Correlation-Id"));
    }
    mvc.perform(get(path).header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void enforcesBuyerAdminAndInternalRoles() throws Exception {
    for (String role : List.of("ADMIN", "ORGANIZER", "INTERNAL_SERVICE", "unknown")) {
      mvc.perform(
              get("/api/v1/orders/one/payment")
                  .header("Authorization", "Bearer " + signedToken(role, "valid")))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
    var buyer =
        mvc.perform(
                get("/api/v1/orders/one/payment")
                    .header("Authorization", "Bearer " + signedToken("BUYER", "valid")))
            .andExpect(status().isNotFound())
            .andReturn();
    assertThat(buyer.getRequest().getSession(false)).isNull();
    mvc.perform(
            get("/internal/v1/events/one/financial-summary")
                .header("Authorization", "Bearer " + signedToken("BUYER", "valid")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/internal/v1/events/one/financial-summary")
                .header("Authorization", "Bearer " + signedToken("INTERNAL_SERVICE", "valid")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/admin/probe")
                .header("Authorization", "Bearer " + signedToken("BUYER", "valid")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/admin/probe")
                .header("Authorization", "Bearer " + signedToken("ADMIN", "valid")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/orders/one/payment").header("X-Role", "BUYER"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void permitsHealthAndBlocksOtherActuatorEndpoints() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
    for (String role : List.of("BUYER", "ADMIN", "INTERNAL_SERVICE"))
      mvc.perform(
              get("/actuator/info").header("Authorization", "Bearer " + signedToken(role, "valid")))
          .andExpect(status().isForbidden());
  }

  @Test
  void ignoresUnknownAndMalformedRealmRoles() {
    var converter = new KeycloakRealmRoleConverter();
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .claim("realm_access", Map.of("roles", List.of("BUYER", "BUYER", "other", 7)))
            .build();
    assertThat(converter.convert(jwt))
        .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
        .containsExactly("ROLE_BUYER");
    assertThat(
            converter.convert(
                Jwt.withTokenValue("test")
                    .header("alg", "RS256")
                    .claim("realm_access", "wrong")
                    .build()))
        .isEmpty();
  }
}
