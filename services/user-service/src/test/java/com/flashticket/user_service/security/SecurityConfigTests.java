package com.flashticket.user_service.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.user_service.config.SecurityConfig;
import com.flashticket.user_service.config.UserJsonConfig;
import com.flashticket.user_service.exception.GlobalExceptionHandler;
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
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@WebMvcTest(SecurityConfigTests.ProbeController.class)
@Import({
  SecurityConfig.class,
  SecurityConfigTests.ProbeController.class,
  UserJsonConfig.class,
  GlobalExceptionHandler.class
})
class SecurityConfigTests {
  static final RSAKey KEY;
  static final HttpServer JWK_SERVER;
  static final String ISSUER;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
      JWK_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      ISSUER = "http://127.0.0.1:" + JWK_SERVER.getAddress().getPort() + "/realms/user";
      byte[] metadata =
          ("{\"issuer\":\""
                  + ISSUER
                  + "\",\"jwks_uri\":\"http://127.0.0.1:"
                  + JWK_SERVER.getAddress().getPort()
                  + "/jwks\"}")
              .getBytes(StandardCharsets.UTF_8);
      JWK_SERVER.createContext(
          "/realms/user/.well-known/openid-configuration",
          exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, metadata.length);
            try (var output = exchange.getResponseBody()) {
              output.write(metadata);
            }
          });
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
      "/api/v1/me",
      "/api/v1/organizer-applications",
      "/api/v1/admin/organizer-applications/{id}/{action}",
      "/api/v1/organizers/{subject}",
      "/api/v1/organizers/{subject}/follow",
      "/internal/v1/probe",
      "/actuator/health",
      "/actuator/health/readiness",
      "/actuator/info",
      "/other"
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void probe() {}
  }

  @ParameterizedTest
  @CsvSource({
    "GET,/api/v1/me,BUYER,204",
    "GET,/api/v1/me,ORGANIZER,204",
    "GET,/api/v1/me,ADMIN,204",
    "GET,/api/v1/me,INTERNAL_SERVICE,403",
    "GET,/api/v1/me,anonymous,401",
    "POST,/api/v1/organizer-applications,BUYER,204",
    "POST,/api/v1/organizer-applications,ADMIN,403",
    "POST,/api/v1/organizer-applications,ORGANIZER,403",
    "POST,/api/v1/organizer-applications,anonymous,401",
    "POST,/api/v1/admin/organizer-applications/one/approve,ADMIN,204",
    "POST,/api/v1/admin/organizer-applications/one/reject,ADMIN,204",
    "POST,/api/v1/admin/organizer-applications/one/approve,BUYER,403",
    "POST,/api/v1/admin/organizer-applications/one/approve,INTERNAL_SERVICE,403",
    "GET,/api/v1/organizers/subject,anonymous,204",
    "GET,/api/v1/organizers/subject,BUYER,204",
    "POST,/api/v1/organizers/subject,BUYER,403",
    "PUT,/api/v1/organizers/subject/follow,BUYER,204",
    "DELETE,/api/v1/organizers/subject/follow,BUYER,204",
    "PUT,/api/v1/organizers/subject/follow,anonymous,401",
    "PUT,/api/v1/organizers/subject/follow,INTERNAL_SERVICE,403",
    "GET,/api/v1/organizers/subject/follow,BUYER,403",
    "GET,/internal/v1/probe,BUYER,403",
    "GET,/internal/v1/probe,INTERNAL_SERVICE,403",
    "GET,/actuator/info,ADMIN,403",
    "GET,/other,BUYER,403",
    "GET,/actuator/health,anonymous,204",
    "GET,/actuator/health/readiness,anonymous,204"
  })
  void routePolicySeparatesPublicBuyerAdminAndMachine(
      String method, String path, String role, int expected) throws Exception {
    var request = request(HttpMethod.valueOf(method), path);
    if (!role.equals("anonymous"))
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(role));
    var response =
        mvc.perform(request)
            .andExpect(status().is(expected))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andReturn();
    assertThat(response.getRequest().getSession(false)).isNull();
    if (expected == 401 || expected == 403) {
      assertThat(response.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
      assertThat(response.getResponse().getContentAsString())
          .contains(expected == 401 ? "UNAUTHENTICATED" : "ACCESS_DENIED");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"signature", "issuer", "audience", "expired", "future"})
  void invalidSignedTokensAreRejected(String variant) throws Exception {
    mvc.perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signed(variant, "BUYER")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
  }

  @ParameterizedTest
  @ValueSource(strings = {"roles-missing", "roles-malformed"})
  void malformedClaimsDoNotGrantHumanAccess(String variant) throws Exception {
    mvc.perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signed(variant, "BUYER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void headersCannotSupplyIdentityOrRoles() throws Exception {
    mvc.perform(get("/api/v1/me").header("X-Role", "ADMIN").header("X-User", "other"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void errorDispatchKeepsTheOriginalStatus() throws Exception {
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
        .andExpect(jsonPath("$.status").value(404));
  }

  private static String token(String role) throws Exception {
    return signed("valid", role);
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
            .claim(
                "realm_access",
                switch (variant) {
                  case "roles-missing" -> null;
                  case "roles-malformed" -> Map.of("roles", "BUYER");
                  default -> Map.of("roles", List.of(role));
                })
            .build();
    SignedJWT jwt =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
    jwt.sign(
        new RSASSASigner(variant.equals("signature") ? new RSAKeyGenerator(2048).generate() : KEY));
    return jwt.serialize();
  }
}
