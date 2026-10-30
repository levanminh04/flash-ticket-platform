package com.flashticket.user_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "user.test.mock-jwt=false")
@AutoConfigureMockMvc
@Import(LocalTestInfrastructure.class)
class SignedJwtApiTests {
  private static final String ISSUER = "https://issuer.test/realms/flash-ticket";
  private static final RSAKey KEY;
  private static final HttpServer JWKS;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
      JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      JWKS.createContext(
          "/jwks",
          exchange -> {
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      JWKS.start();
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.audiences[0]", () -> "flash-ticket-api");
  }

  @AfterAll
  static void closeJwks() {
    JWKS.stop(0);
  }

  @Autowired MockMvc mvc;

  private String token(
      String issuer, String audience, String role, Instant expiry, boolean tampered)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("signed-buyer")
            .issueTime(Date.from(Instant.now().minusSeconds(10)))
            .expirationTime(Date.from(expiry))
            .claim("realm_access", Map.of("roles", List.of(role)))
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID("test-key")
                .type(JOSEObjectType.JWT)
                .build(),
            claims);
    jwt.sign(new RSASSASigner(tampered ? new RSAKeyGenerator(2048).generate() : KEY));
    return jwt.serialize();
  }

  @Test
  void correctSignatureIssuerAudienceAndHumanRoleAllowProfile() throws Exception {
    mvc.perform(
            get("/api/v1/me")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            ISSUER,
                            "flash-ticket-api",
                            "BUYER",
                            Instant.now().plusSeconds(300),
                            false)))
        .andExpect(status().isOk());
  }

  @Test
  void invalidIssuerAudienceExpiryAndSignatureAreRejected() throws Exception {
    for (String jwt :
        List.of(
            token(
                "https://wrong-issuer.test",
                "flash-ticket-api",
                "BUYER",
                Instant.now().plusSeconds(300),
                false),
            token(ISSUER, "wrong-audience", "BUYER", Instant.now().plusSeconds(300), false),
            token(ISSUER, "flash-ticket-api", "BUYER", Instant.now().minusSeconds(300), false),
            token(ISSUER, "flash-ticket-api", "BUYER", Instant.now().plusSeconds(300), true))) {
      mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + jwt))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void verifiedMachineAndUnknownRolesCannotUseHumanApi() throws Exception {
    for (String role : List.of("INTERNAL_SERVICE", "SUPER_ADMIN")) {
      mvc.perform(
              get("/api/v1/me")
                  .header(
                      "Authorization",
                      "Bearer "
                          + token(
                              ISSUER,
                              "flash-ticket-api",
                              role,
                              Instant.now().plusSeconds(300),
                              false)))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void signedMachineTokenWithAccidentalBuyerRoleCannotCreateHumanProfile() throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience("flash-ticket-api")
            .subject("machine-with-buyer")
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .claim("realm_access", Map.of("roles", List.of("INTERNAL_SERVICE", "BUYER")))
            .build();
    var signed =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
    signed.sign(new RSASSASigner(KEY));
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + signed.serialize()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/internal/v1/users/machine-with-buyer")
                .header("Authorization", "Bearer " + signed.serialize()))
        .andExpect(status().isNotFound());
  }
}
