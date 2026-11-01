package com.flashticket.ticket_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TicketApiTests {
  private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final UUID CORRELATION = UUID.fromString("00000000-0000-0000-0000-000000000005");
  private static final String ACTOR = "buyer-subject";
  private FakeOperations operations;
  private MockMvc mvc;
  private JwtAuthenticationToken principal;

  @BeforeEach
  void setup() {
    operations = new FakeOperations();
    mvc =
        MockMvcBuilders.standaloneSetup(new TicketApiController(operations))
            .setControllerAdvice(new TicketApiExceptionHandler())
            .build();
    Jwt jwt = Jwt.withTokenValue("test-only").header("alg", "RS256").subject(ACTOR).build();
    principal = new JwtAuthenticationToken(jwt);
  }

  @Test
  void buyerListContainsOnlyContractFieldsAndPassesVerifiedSubject() throws Exception {
    mvc.perform(
            get("/api/v1/orders/" + ORDER + "/tickets")
                .principal(principal)
                .header("X-Correlation-Id", CORRELATION))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Correlation-Id", CORRELATION.toString()))
        .andExpect(jsonPath("$.issuanceState").value("COMPLETED"))
        .andExpect(jsonPath("$.tickets[0].ticketId").value(TICKET.toString()))
        .andExpect(jsonPath("$.tickets[0].ticketTypeName").value("General admission"))
        .andExpect(jsonPath("$.tickets[0].qrToken").doesNotExist());
    assertThat(operations.orderId).isEqualTo(ORDER);
    assertThat(operations.actor).isEqualTo(ACTOR);
  }

  @Test
  void qrDownloadIsJsonAndCannotBeCached() throws Exception {
    mvc.perform(
            get("/api/v1/tickets/" + TICKET + "/qr")
                .principal(principal)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.qrToken").value("opaque-token"));
    assertThat(operations.ticketId).isEqualTo(TICKET);
    assertThat(operations.actor).isEqualTo(ACTOR);
    mvc.perform(
            get("/api/v1/tickets/" + TICKET + "/qr")
                .principal(principal)
                .accept(MediaType.IMAGE_PNG))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void redeliveryReturnsAcceptedAndPreservesIdempotencyKey() throws Exception {
    mvc.perform(
            post("/api/v1/orders/" + ORDER + "/ticket-deliveries")
                .principal(principal)
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.deliveryId").value(KEY.toString()))
        .andExpect(jsonPath("$.state").value("PENDING"));
    assertThat(operations.key).isEqualTo(KEY);
    assertThat(operations.actor).isEqualTo(ACTOR);
    assertThat(operations.orderId).isEqualTo(ORDER);
  }

  @Test
  void invalidQrSyntaxReachesAuditOperationWithCorrelation() throws Exception {
    mvc.perform(
            post("/api/v1/events/" + EVENT + "/check-ins")
                .principal(principal)
                .header("Idempotency-Key", KEY)
                .header("X-Correlation-Id", CORRELATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"invalid token !\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("UNKNOWN_QR"))
        .andExpect(jsonPath("$.succeeded").value(false));
    assertThat(operations.qrToken).isEqualTo("invalid token !");
    assertThat(operations.eventId).isEqualTo(EVENT);
    assertThat(operations.correlationId).isEqualTo(CORRELATION);
    assertThat(operations.key).isEqualTo(KEY);
    assertThat(operations.actor).isEqualTo(ACTOR);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1-1-1-1-1", "not-uuid", "00000000-0000-0000-0000-00000000000z"})
  void rejectsNonCanonicalUuidPathBeforeOperations(String id) throws Exception {
    mvc.perform(get("/api/v1/orders/" + id + "/tickets").principal(principal))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    assertThat(operations.calls).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "[]",
        "null",
        "{\"qrToken\":1}",
        "{\"qrToken\":null}",
        "{\"qrToken\":\"x\",\"extra\":true}",
        "{broken"
      })
  void rejectsInvalidCheckInJsonWithoutEchoingBody(String body) throws Exception {
    mvc.perform(
            post("/api/v1/events/" + EVENT + "/check-ins")
                .principal(principal)
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.violations").isArray());
    assertThat(operations.calls).isZero();
  }

  @Test
  void rejectsOversizedQrBeforeBusinessOperation() throws Exception {
    mvc.perform(
            post("/api/v1/events/" + EVENT + "/check-ins")
                .principal(principal)
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"" + "a".repeat(4097) + "\"}"))
        .andExpect(status().isBadRequest());
    assertThat(operations.calls).isZero();
  }

  @Test
  void requiresStrictIdempotencyHeaderAndEmptyRedeliveryObject() throws Exception {
    for (String key : List.of("", "1-1-1-1-1", "not-uuid")) {
      mvc.perform(
              post("/api/v1/orders/" + ORDER + "/ticket-deliveries")
                  .principal(principal)
                  .header("Idempotency-Key", key)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post("/api/v1/orders/" + ORDER + "/ticket-deliveries")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/orders/" + ORDER + "/ticket-deliveries")
                .principal(principal)
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"elsewhere@example.invalid\"}"))
        .andExpect(status().isBadRequest());
    assertThat(operations.calls).isZero();
  }

  @Test
  void internalProbePassesContractThroughAndRejectsShortUuid() throws Exception {
    mvc.perform(get("/internal/v1/orders/" + ORDER + "/issuance").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("PENDING"))
        .andExpect(jsonPath("$.orderId").value(ORDER.toString()));
    mvc.perform(get("/internal/v1/orders/1-1-1-1-1/issuance").principal(principal))
        .andExpect(status().isBadRequest());
  }

  @Test
  void domainErrorsAreSafeAndUnexpectedFailuresDoNotLeak() throws Exception {
    operations.failure = new TicketProblem(409, "IDEMPOTENCY_CONFLICT");
    mvc.perform(get("/api/v1/orders/" + ORDER + "/tickets").principal(principal))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    operations.failure = new IllegalStateException("raw-qr-token and private JDBC password");
    mvc.perform(
            get("/api/v1/orders/" + ORDER + "/tickets")
                .principal(principal)
                .header("X-Correlation-Id", "not-a-uuid"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
        .andExpect(content().string(not(containsString("raw-qr-token"))))
        .andExpect(content().string(not(containsString("password"))))
        .andExpect(header().exists("X-Correlation-Id"));
  }

  @Test
  void securityExceptionKeepsForbiddenStatus() throws Exception {
    operations.failure = new AccessDeniedException("private reason");
    mvc.perform(get("/api/v1/orders/" + ORDER + "/tickets").principal(principal))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void businessEndpointsAreAbsentUnlessFeatureIsExplicitlyEnabled() {
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withUserConfiguration(TicketApiController.class, TicketApiExceptionHandler.class)
        .withBean(TicketOperations.class, FakeOperations::new)
        .run(
            context -> {
              assertThat(context.getBeansOfType(TicketApiController.class)).isEmpty();
              assertThat(context.getBeansOfType(TicketApiExceptionHandler.class)).isEmpty();
            });
  }

  static final class FakeOperations implements TicketOperations {
    UUID orderId, ticketId, eventId, key, correlationId;
    String actor, qrToken;
    int calls;
    RuntimeException failure;

    private void called() {
      calls++;
      if (failure != null) throw failure;
    }

    @Override
    public TicketViews.TicketList listTickets(UUID orderId, String actor) {
      called();
      this.orderId = orderId;
      this.actor = actor;
      return new TicketViews.TicketList(
          "COMPLETED",
          List.of(
              new TicketViews.TicketView(
                  TICKET, EVENT, KEY, 1, "General admission", null, "VALID")));
    }

    @Override
    public String qr(UUID ticketId, String actor) {
      called();
      this.ticketId = ticketId;
      this.actor = actor;
      return "opaque-token";
    }

    @Override
    public TicketViews.DeliveryReceipt redeliver(UUID orderId, String actor, UUID key) {
      called();
      this.orderId = orderId;
      this.actor = actor;
      this.key = key;
      return new TicketViews.DeliveryReceipt(KEY, "PENDING");
    }

    @Override
    public TicketViews.CheckInResult checkIn(
        UUID eventId, String actor, UUID key, String qrToken, UUID correlationId) {
      called();
      this.eventId = eventId;
      this.actor = actor;
      this.key = key;
      this.qrToken = qrToken;
      this.correlationId = correlationId;
      return new TicketViews.CheckInResult(
          KEY, eventId, null, "UNKNOWN_QR", false, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Override
    public Object probe(UUID orderId) {
      called();
      return Map.of("state", "PENDING", "orderId", orderId, "issuanceId", KEY);
    }
  }
}
