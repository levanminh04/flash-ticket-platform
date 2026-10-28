package com.flashticket.payment_service.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.payment_service.config.PaymentReadRepositoryTestConfiguration;
import com.flashticket.payment_service.dto.message.StartPaymentResultState;
import com.flashticket.payment_service.entity.PaymentReadFixtures;
import com.flashticket.payment_service.repository.*;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PaymentReadRepositoryTestConfiguration.class)
class PaymentJwtSecurityTest {
  static final String ISSUER = "https://issuer.example.test";
  static final RSAKey KEY = key();
  static final HttpServer SERVER = startServer();
  final UUID order = UUID.randomUUID();
  @Autowired MockMvc mvc;
  @Autowired OrderPaymentSnapshotRepository snapshots;
  @Autowired PaymentAttemptRepository attempts;
  @Autowired PaymentConfirmationRepository confirmations;
  @Autowired RefundRepository refunds;
  @Autowired InboxMessageRepository inbox;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("flashticket.payment.jwt.issuer-uri", () -> ISSUER);
    r.add(
        "flashticket.payment.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/jwks");
    r.add("flashticket.payment.jwt.audience", () -> "payment");
  }

  static RSAKey key() {
    try {
      return new RSAKeyGenerator(2048).keyID("p2-key").generate();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static HttpServer startServer() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/jwks",
          e -> {
            var bytes = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type", "application/json");
            e.sendResponseHeaders(200, bytes.length);
            try (var out = e.getResponseBody()) {
              out.write(bytes);
            }
          });
      server.start();
      return server;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterAll
  static void close() {
    SERVER.stop(0);
  }

  @BeforeEach
  void setup() {
    reset(snapshots, attempts, confirmations, refunds, inbox);
    var access = mock(OrderPaymentSnapshotRepository.OrderPaymentAccess.class);
    when(access.getOrderId()).thenReturn(order);
    when(access.getBuyerSubject()).thenReturn("buyer");
    when(access.getExpiresAt()).thenReturn(Instant.now().plusSeconds(600));
    when(snapshots.findAccessByOrderId(order)).thenReturn(Optional.of(access));
  }

  String token(
      String subject,
      String issuer,
      String audience,
      List<String> roles,
      Instant expiry,
      RSAKey key)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .issueTime(Date.from(Instant.now().minusSeconds(3600)))
            .claim("realm_access", Map.of("roles", roles));
    if (subject != null) claims.subject(subject);
    if (expiry != null) claims.expirationTime(Date.from(expiry));
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
            claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  String valid() throws Exception {
    return token("buyer", ISSUER, "payment", List.of("BUYER"), Instant.now().plusSeconds(600), KEY);
  }

  String path() {
    return "/api/v1/orders/" + order + "/payment";
  }

  @Test
  void acceptsSignedOwnerTokenAndReturnsNoStoreDto() throws Exception {
    mvc.perform(get(path()).header("Authorization", "Bearer " + valid()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.orderId").value(order.toString()))
        .andExpect(jsonPath("$.data.latestAttempt").isEmpty())
        .andExpect(jsonPath("$.data.startRequest").isEmpty())
        .andExpect(jsonPath("$.data.refunds").isArray());
  }

  @Test
  void rejectsInvalidBearerClaimsAndSignature() throws Exception {
    var future = Instant.now().plusSeconds(600);
    var tokens =
        List.of(
            token("buyer", ISSUER, null, List.of("BUYER"), future, KEY),
            token("buyer", ISSUER, "wrong", List.of("BUYER"), future, KEY),
            token("buyer", "https://wrong.example.test", "payment", List.of("BUYER"), future, KEY),
            token(
                "buyer", ISSUER, "payment", List.of("BUYER"), Instant.now().minusSeconds(600), KEY),
            token(" ", ISSUER, "payment", List.of("BUYER"), future, KEY),
            token(null, ISSUER, "payment", List.of("BUYER"), future, KEY),
            token("buyer", ISSUER, "payment", List.of("BUYER"), null, KEY),
            token("buyer", ISSUER, "payment", List.of("BUYER"), future, key()));
    for (var token : tokens)
      mvc.perform(get(path()).header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.data.code").value("AUTHENTICATION_REQUIRED"))
          .andExpect(jsonPath("$.data.correlationId").value(order.toString()));
    verifyNoInteractions(snapshots, attempts, confirmations, refunds, inbox);
  }

  @Test
  void requiresBuyerRoleAndOwnerIndependently() throws Exception {
    for (var roles : List.of(List.<String>of(), List.of("ADMIN")))
      mvc.perform(
              get(path())
                  .header(
                      "Authorization",
                      "Bearer "
                          + token(
                              "buyer",
                              ISSUER,
                              "payment",
                              roles,
                              Instant.now().plusSeconds(600),
                              KEY)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.data.code").value("ACCESS_DENIED"));
    mvc.perform(
            get(path())
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "other",
                            ISSUER,
                            "payment",
                            List.of("BUYER"),
                            Instant.now().plusSeconds(600),
                            KEY)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.data.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void returnsProblemsBeforeControllerAndForInvalidIds() throws Exception {
    mvc.perform(get(path()).header("X-Correlation-Id", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.data.correlationId").value(order.toString()))
        .andExpect(jsonPath("$.data.violations").isArray());
    mvc.perform(
            get(path()).param("requestId", "invalid").header("Authorization", "Bearer " + valid()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    mvc.perform(
            get("/api/v1/orders/1-1-1-1-1/payment").header("Authorization", "Bearer " + valid()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/orders/not-uuid/payment")
                .header("Authorization", "Bearer " + valid())
                .header("X-Correlation-Id", "bad-header"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result -> {
              var body = result.getResponse().getContentAsString();
              assertThat(body).doesNotContain("bad-header");
            });
  }

  @Test
  void opensOnlyVnpayCallbacksAndRequiresBuyerForPaymentCreation() throws Exception {
    mvc.perform(get("/api/v1/payments/vnpay/ipn"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.RspCode").value("97"));
    mvc.perform(get("/api/v1/payments/vnpay/return"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "flash-ticket://payment-return?status=FAILED"));
    mvc.perform(
            post("/api/v1/payments")
                .contentType("application/json")
                .content("{\"orderId\":\"" + order + "\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/payments")
                .contentType("application/json")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "buyer",
                            ISSUER,
                            "payment",
                            List.of("ORGANIZER"),
                            Instant.now().plusSeconds(600),
                            KEY))
                .content("{\"orderId\":\"" + order + "\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/payments/vnpay/anything-else")).andExpect(status().isUnauthorized());
    var snapshot = PaymentReadFixtures.snapshot(null, "buyer");
    snapshot.setOrderId(order);
    when(snapshots.findForUpdateByOrderId(order)).thenReturn(Optional.of(snapshot));
    when(attempts.save(any())).thenAnswer(i -> i.getArgument(0));
    mvc.perform(
            post("/api/v1/payments")
                .contentType("application/json")
                .header("Authorization", "Bearer " + valid())
                .content("{\"orderId\":\"" + order + "\",\"clientIp\":\"203.0.113.5\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value(order.toString()))
        .andExpect(jsonPath("$.data.paymentUrl").isNotEmpty());
  }

  @Test
  void isolatesRequestResultAndDoesNotExposeWriteOrInternalRoutes() throws Exception {
    var request = UUID.randomUUID();
    mvc.perform(
            get(path())
                .param("requestId", request.toString())
                .header("Authorization", "Bearer " + valid()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.startRequest.state").value("WAITING"));
    when(inbox.findById(any()))
        .thenReturn(
            Optional.of(
                PaymentReadFixtures.inbox(
                    request, UUID.randomUUID(), StartPaymentResultState.REJECTED, null)));
    mvc.perform(
            get(path())
                .param("requestId", request.toString())
                .header("Authorization", "Bearer " + valid()))
        .andExpect(status().isNotFound());
    mvc.perform(post(path()).header("Authorization", "Bearer " + valid()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/internal/v1/events/" + order + "/financial-summary")
                .header("Authorization", "Bearer " + valid()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/health").header("Authorization", "Bearer " + valid()))
        .andExpect(status().isForbidden());
  }
}
