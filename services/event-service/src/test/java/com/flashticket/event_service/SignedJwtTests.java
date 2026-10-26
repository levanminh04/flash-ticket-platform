package com.flashticket.event_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(IsolatedInfrastructure.class)
class SignedJwtTests {
  static final RSAKey KEY;
  static final HttpServer SERVER;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("isolated-event").generate();
      SERVER = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      SERVER.createContext(
          "/jwks",
          exchange -> {
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
              output.write(body);
            }
          });
      SERVER.start();
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @AfterAll
  static void stop() {
    SERVER.stop(0);
  }

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry properties) {
    properties.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> "https://issuer.test/realms/event");
    properties.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/jwks");
    properties.add(
        "spring.security.oauth2.resourceserver.jwt.audiences[0]", () -> "flash-ticket-api");
  }

  @Autowired MockMvc mvc;

  String token(String issuer, String audience, long expires, Object roles) throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("service-account")
            .issueTime(Date.from(Instant.now().minusSeconds(30)))
            .expirationTime(Date.from(Instant.now().plusSeconds(expires)))
            .claim("realm_access", Map.of("roles", roles))
            .build();
    var signed =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    signed.sign(new RSASSASigner(KEY));
    return signed.serialize();
  }

  @Test
  void correctSignatureIssuerAudienceAndRoleReachHealth() throws Exception {
    mvc.perform(
            get("/actuator/health")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://issuer.test/realms/event",
                            "flash-ticket-api",
                            300,
                            List.of("INTERNAL_SERVICE"))))
        .andExpect(status().isOk());
  }

  @Test
  void wrongAudienceFailsClosed() throws Exception {
    mvc.perform(
            get("/actuator/health")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://issuer.test/realms/event",
                            "other-api",
                            300,
                            List.of("INTERNAL_SERVICE"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void wrongIssuerFailsClosed() throws Exception {
    mvc.perform(
            get("/actuator/health")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://other.test",
                            "flash-ticket-api",
                            300,
                            List.of("INTERNAL_SERVICE"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredTokenFailsClosed() throws Exception {
    mvc.perform(
            get("/actuator/health")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://issuer.test/realms/event",
                            "flash-ticket-api",
                            -600,
                            List.of("INTERNAL_SERVICE"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void humanRoleCannotReadManagement() throws Exception {
    mvc.perform(
            get("/actuator/health")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://issuer.test/realms/event",
                            "flash-ticket-api",
                            300,
                            List.of("BUYER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void malformedRolesGrantNothing() throws Exception {
    mvc.perform(
            get("/actuator/health")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://issuer.test/realms/event",
                            "flash-ticket-api",
                            300,
                            "INTERNAL_SERVICE")))
        .andExpect(status().isForbidden());
  }
}
