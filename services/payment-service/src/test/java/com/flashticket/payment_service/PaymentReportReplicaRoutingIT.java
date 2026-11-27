package com.flashticket.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashticket.payment_service.support.PaymentHaInstanceConfiguration;
import com.flashticket.payment_service.support.PaymentPostgresFixture;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("payment-flow-test")
@Import(PaymentHaInstanceConfiguration.class)
class PaymentReportReplicaRoutingIT {
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    PaymentPostgresFixture.registerProperties(registry, true);
  }

  @BeforeEach
  void resetFixture() throws Exception {
    PaymentPostgresFixture.resetDatabase();
  }

  @Test
  void threeReportApisReadPhysicalStandbyWithOneSnapshot() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID firstOrder = PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
    UUID secondOrder = PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
    var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
    UUID runId = UUID.randomUUID();
    jdbc.update(
        "insert into payment_schema.tbl_event_refund_runs(id,event_id,source_cancellation_id,selection_complete,selected_order_count) values (?,?,?,true,2)",
        runId,
        eventId,
        UUID.randomUUID());
    for (UUID orderId : new UUID[] {firstOrder, secondOrder}) {
      UUID refundId = UUID.randomUUID();
      jdbc.update(
          "insert into payment_schema.tbl_refunds(id,charge_id,order_id,amount,currency,reason,state,created_at) select ?,id,order_id,amount,currency,'EVENT_CANCELLED','FAILED',? from payment_schema.tbl_charges where order_id=?",
          refundId,
          java.sql.Timestamp.from(
              java.time.Instant.parse(
                  orderId.equals(firstOrder) ? "2026-11-01T01:00:00Z" : "2026-11-01T02:00:00Z")),
          orderId);
      jdbc.update(
          "insert into payment_schema.tbl_event_refund_items(run_id,event_id,order_id,confirmed_charge_id,refund_id,state) select ?,?,order_id,id,?,'NEEDS_ATTENTION' from payment_schema.tbl_charges where order_id=?",
          runId,
          eventId,
          refundId,
          orderId);
      for (int attempt = 1; attempt <= 2; attempt++) {
        var startedAt = java.time.Instant.parse("2026-11-02T00:00:00Z").plusSeconds(attempt);
        jdbc.update(
            "insert into payment_schema.tbl_refund_attempts(id,refund_id,request_key,started_at,finished_at,outcome) values (?,?,?,?,?,?)",
            UUID.randomUUID(),
            refundId,
            UUID.randomUUID().toString(),
            java.sql.Timestamp.from(startedAt),
            java.sql.Timestamp.from(startedAt.plusSeconds(1)),
            attempt == 1 ? "OLDER_FAILURE" : "LATEST_FAILURE");
      }
    }
    PaymentPostgresFixture.awaitReplay();
    if (context.containsBean("paymentReportReplicaHealth")) {
      ReflectionTestUtils.invokeMethod(context.getBean("paymentReportReplicaHealth"), "refresh");
    }
    for (String path :
        new String[] {
          "/api/v1/admin/refunds?size=1",
          "/api/v1/admin/events/" + eventId + "/refund-progress",
          "/api/v1/events/" + eventId + "/reconciliation"
        }) {
      var observations =
          PaymentPostgresFixture.inspectRead(
              () -> {
                var response =
                    mvc.perform(
                            get(path)
                                .with(
                                    jwt()
                                        .jwt(token -> token.subject("admin-1"))
                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                        .andExpect(status().isOk())
                        .andReturn();
                var payload =
                    context
                        .getBean(tools.jackson.databind.json.JsonMapper.class)
                        .readTree(response.getResponse().getContentAsString())
                        .path("data");
                if (path.startsWith("/api/v1/admin/refunds")) {
                  assertThat(payload.path("items").size()).isEqualTo(1);
                  assertThat(payload.path("totalElements").asLong()).isEqualTo(2);
                  assertThat(payload.path("totalPages").asInt()).isEqualTo(2);
                  assertThat(payload.path("items").get(0).path("orderId").asText())
                      .isEqualTo(firstOrder.toString());
                  assertThat(payload.path("items").get(0).path("amount").asText())
                      .isEqualTo("100000");
                  assertThat(payload.path("items").get(0).path("lastAttemptOutcome").asText())
                      .isEqualTo("LATEST_FAILURE");
                } else if (path.endsWith("/refund-progress")) {
                  assertThat(payload.path("selectedOrderCount").asLong()).isEqualTo(2);
                  assertThat(payload.path("needsAttentionCount").asLong()).isEqualTo(2);
                  assertThat(payload.path("pendingCount").asLong()).isZero();
                } else {
                  assertThat(payload.path("validGross").asText()).isEqualTo("200000");
                  assertThat(payload.path("feeAmount").asText()).isEqualTo("15000");
                  assertThat(payload.path("netAmount").asText()).isEqualTo("185000");
                }
                return response;
              });
      assertThat(observations).as("SQL payload thật từ HTTP " + path).isNotEmpty();
      Object pid = observations.getFirst().get("pid");
      Object snapshot = observations.getFirst().get("snapshot");
      boolean countObserved = false;
      boolean attemptObserved = false;
      for (var observation : observations) {
        String sql = observation.get("sql").toString().toLowerCase(java.util.Locale.ROOT);
        countObserved |= sql.contains("count(");
        attemptObserved |= sql.contains("tbl_refund_attempts");
        assertThat(observation.get("replica"))
            .as("Report phải chạy physical standby: " + path)
            .isEqualTo(true);
        assertThat(observation.get("isolation")).isEqualTo("repeatable read");
        assertThat(observation.get("readOnly")).isEqualTo("on");
        assertThat(observation.get("pid")).isEqualTo(pid);
        assertThat(observation.get("snapshot")).isEqualTo(snapshot);
      }
      if (path.startsWith("/api/v1/admin/refunds")) {
        assertThat(countObserved).as("Page count SQL phải cùng reader snapshot").isTrue();
        assertThat(attemptObserved).as("Latest attempt SQL phải cùng reader snapshot").isTrue();
      }
    }
  }

  @Test
  void paymentStatusCallbacksInternalReadsAndWritesStayOnWriter() throws Exception {
    var payments = context.getBean(com.flashticket.payment_service.service.PaymentService.class);
    var finance = context.getBean(com.flashticket.payment_service.service.FinanceService.class);
    var refunds = context.getBean(com.flashticket.payment_service.service.RefundService.class);
    var purchase = PaymentPostgresFixture.purchase(context, UUID.randomUUID(), "buyer-1");
    org.mockito.Mockito.when(
            context
                .getBean(com.flashticket.payment_service.client.BookingPurchaseClient.class)
                .preparePurchaseSnapshot(purchase.orderId(), "buyer-1"))
        .thenReturn(purchase);
    var started =
        new java.util.concurrent.atomic.AtomicReference<
            com.flashticket.payment_service.dto.response.PaymentStart>();
    var writes =
        PaymentPostgresFixture.inspectRead(
            () -> {
              started.set(
                  payments.createPayment(
                      new com.flashticket.payment_service.dto.request.CreatePaymentRequest(
                          purchase.orderId(), null),
                      new org.springframework.mock.web.MockHttpServletRequest(),
                      "buyer-1",
                      UUID.randomUUID()));
              var ipn = PaymentPostgresFixture.signedIpn(started.get());
              assertThat(payments.handleIPN(ipn)).contains("\"RspCode\":\"00\"");
              payments.handleReturn(ipn);
              payments.buildAppReturnUri(ipn);
              payments.getPayment(purchase.orderId(), UUID.randomUUID(), "buyer-1");
              refunds.findEventRefundRunsToSelect(20);
              refunds.findRefundsToSend(20);
              refunds.findRefundsToPoll(java.time.Instant.now(), 20);
              context
                  .getBean(com.flashticket.payment_service.service.PaymentSagaService.class)
                  .findRecoverableOrderIds(java.time.Instant.now(), 20);
              return null;
            });
    assertThat(writes).isNotEmpty();
    for (var sql : writes) assertThat(sql.get("replica")).isEqualTo(false);
    UUID eventId = UUID.randomUUID();
    PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
    PaymentPostgresFixture.awaitReplay();
    ReflectionTestUtils.invokeMethod(context.getBean("paymentReportReplicaHealth"), "refresh");
    var internal =
        PaymentPostgresFixture.inspectRead(
            () -> {
              assertThat(finance.getFinancialSummary(eventId).netAmount()).isEqualTo("92500");
              var reconciled =
                  finance.confirmReconciliation(
                      eventId,
                      new com.flashticket.payment_service.dto.request.ReconciliationRequest(
                          "fixture-evidence"),
                      "admin-1");
              assertThat(reconciled.feeAmount()).isEqualTo("7500");
              assertThat(finance.markEventPaid(eventId, "admin-1").paidAt()).isNotNull();
              return null;
            });
    for (var sql : internal) assertThat(sql.get("replica")).isEqualTo(false);
    UUID refundEvent = UUID.randomUUID();
    UUID retryOrder =
        PaymentPostgresFixture.seedConfirmedOrder(context, refundEvent, "organizer-1");
    UUID manualOrder =
        PaymentPostgresFixture.seedConfirmedOrder(context, refundEvent, "organizer-1");
    var seedJdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
    UUID retryId = UUID.randomUUID();
    UUID manualId = UUID.randomUUID();
    for (UUID refundId : new UUID[] {retryId, manualId}) {
      seedJdbc.update(
          "insert into payment_schema.tbl_refunds(id,charge_id,order_id,amount,currency,reason,state) select ?,id,order_id,amount,currency,'EVENT_CANCELLED','FAILED' from payment_schema.tbl_charges where order_id=?",
          refundId,
          refundId.equals(retryId) ? retryOrder : manualOrder);
    }
    var refundWrites =
        PaymentPostgresFixture.inspectRead(
            () -> {
              assertThat(refunds.retryRefund(retryId).state())
                  .isEqualTo(com.flashticket.payment_service.entity.enums.RefundState.PENDING);
              var settled =
                  refunds.settleRefundManually(
                      manualId,
                      new com.flashticket.payment_service.dto.request.ManualSettlementRequest(
                          "fixture-manual"),
                      "admin-1");
              assertThat(settled.state())
                  .isEqualTo(com.flashticket.payment_service.entity.enums.RefundState.SUCCEEDED);
              assertThat(settled.amount()).isEqualTo("100000");
              assertThat(settled.settledBy()).isEqualTo("admin-1");
              finance.recordFinanceConfiguration(
                  new com.flashticket.payment_service.dto.common.Envelope<>(
                      UUID.randomUUID(),
                      "event.finance-configured.v1",
                      1,
                      refundEvent,
                      "2",
                      java.time.Instant.now(),
                      UUID.randomUUID(),
                      null,
                      null,
                      new com.flashticket.payment_service.dto.message.FinanceConfiguration(
                          refundEvent,
                          "organizer-1",
                          java.time.Instant.now().minusSeconds(3600),
                          "0.075",
                          "2",
                          "VND")));
              return null;
            });
    assertThat(refundWrites).isNotEmpty();
    for (var sql : refundWrites) assertThat(sql.get("replica")).isEqualTo(false);
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(
            context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
    transaction.setName("paymentReportRead");
    transaction.executeWithoutResult(
        status ->
            assertThat(jdbc.queryForObject("select pg_is_in_recovery()", Boolean.class)).isFalse());
    transaction.setName("ordinary-read-only");
    transaction.setReadOnly(true);
    transaction.executeWithoutResult(
        status ->
            assertThat(jdbc.queryForObject("select pg_is_in_recovery()", Boolean.class)).isFalse());
    mvc.perform(
            get("/api/v1/admin/refunds")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void financeOwnershipChangeRemainsFailClosedDuringReplicaLag() throws Exception {
    UUID eventId = UUID.randomUUID();
    PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "owner-a");
    PaymentPostgresFixture.awaitReplay();
    Object health = context.getBean("paymentReportReplicaHealth");
    ReflectionTestUtils.invokeMethod(health, "refresh");
    PaymentPostgresFixture.psql(true, "select pg_wal_replay_pause()");
    var envelope =
        new com.flashticket.payment_service.dto.common.Envelope<>(
            UUID.randomUUID(),
            "event.finance-configured.v1",
            1,
            eventId,
            "2",
            java.time.Instant.now(),
            UUID.randomUUID(),
            null,
            null,
            new com.flashticket.payment_service.dto.message.FinanceConfiguration(
                eventId,
                "owner-b",
                java.time.Instant.now().minusSeconds(3600),
                "0.075",
                "2",
                "VND"));
    try {
      context
          .getBean(com.flashticket.payment_service.service.FinanceService.class)
          .recordFinanceConfiguration(envelope);
      var oldOwner =
          PaymentPostgresFixture.inspectRead(
              () ->
                  mvc.perform(
                          get("/api/v1/events/" + eventId + "/reconciliation")
                              .with(
                                  jwt()
                                      .jwt(token -> token.subject("owner-a"))
                                      .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                      .andExpect(status().isNotFound())
                      .andReturn());
      assertThat(oldOwner).hasSize(1);
      assertThat(oldOwner.getFirst().get("replica")).isEqualTo(false);
      var newOwner =
          PaymentPostgresFixture.inspectRead(
              () ->
                  mvc.perform(
                          get("/api/v1/events/" + eventId + "/reconciliation")
                              .with(
                                  jwt()
                                      .jwt(token -> token.subject("owner-b"))
                                      .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                      .andExpect(status().isNotFound())
                      .andReturn());
      assertThat(newOwner).hasSize(2);
      assertThat(newOwner.getFirst().get("replica")).isEqualTo(false);
      assertThat(newOwner.getLast().get("replica")).isEqualTo(true);
      assertThat((Boolean) ReflectionTestUtils.invokeMethod(health, "tryBeginRead", new Object[0]))
          .isEqualTo(false);
    } finally {
      PaymentPostgresFixture.psql(true, "select pg_wal_replay_resume()");
      PaymentPostgresFixture.awaitReplay();
    }
    ReflectionTestUtils.invokeMethod(health, "refresh");
    mvc.perform(
            get("/api/v1/events/" + eventId + "/reconciliation")
                .with(
                    jwt()
                        .jwt(token -> token.subject("owner-b"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/events/" + eventId + "/reconciliation")
                .with(
                    jwt()
                        .jwt(token -> token.subject("owner-a"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void disabledPausedExpiredOrBusyReplicaFallsBackBeforeReading() throws Exception {
    try (var off = PaymentPostgresFixture.openServiceContext(false)) {
      assertThat(off.containsBean("paymentReportReaderDataSource")).isFalse();
      assertThat(off.containsBean("paymentReportReplicaHealth")).isFalse();
      off.getBean(com.flashticket.payment_service.service.PaymentReportReadService.class)
          .getRefunds(com.flashticket.payment_service.entity.enums.RefundState.FAILED, 0, 20);
      assertThat(
              off.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                  .queryForObject("select pg_is_in_recovery()", Boolean.class))
          .isFalse();
    }
    var health =
        context.getBean(com.flashticket.payment_service.config.PaymentReportReplicaHealth.class);
    for (String reason :
        new String[] {"paused", "expired", "busy", "wrong-role", "endpoint-down"}) {
      PaymentPostgresFixture.awaitReplay();
      ReflectionTestUtils.invokeMethod(health, "refresh");
      var originalReader = ReflectionTestUtils.getField(health, "reader");
      org.testcontainers.containers.GenericContainer<?> replica =
          (org.testcontainers.containers.GenericContainer<?>)
              ReflectionTestUtils.getField(PaymentPostgresFixture.class, "standby");
      try {
        if (reason.equals("paused")) {
          PaymentPostgresFixture.psql(true, "select pg_wal_replay_pause()");
          ReflectionTestUtils.invokeMethod(health, "refresh");
        }
        if (reason.equals("expired")) ReflectionTestUtils.setField(health, "maxSampleAgeNanos", 1L);
        if (reason.equals("busy")) assertThat(health.tryBeginRead()).isTrue();
        if (reason.equals("wrong-role")) {
          ReflectionTestUtils.setField(
              health, "reader", context.getBean("paymentWriterDataSource"));
          ReflectionTestUtils.invokeMethod(health, "refresh");
        }
        if (reason.equals("endpoint-down")) {
          replica.getDockerClient().pauseContainerCmd(replica.getContainerId()).exec();
          ReflectionTestUtils.invokeMethod(health, "refresh");
        }
        var observations =
            PaymentPostgresFixture.inspectRead(
                () ->
                    mvc.perform(
                            get("/api/v1/admin/refunds")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                        .andExpect(status().isOk())
                        .andReturn());
        assertThat(observations).isNotEmpty();
        for (var sql : observations) assertThat(sql.get("replica")).as(reason).isEqualTo(false);
      } finally {
        if (reason.equals("endpoint-down"))
          replica.getDockerClient().unpauseContainerCmd(replica.getContainerId()).exec();
        health.finishRead();
        ReflectionTestUtils.setField(health, "reader", originalReader);
        ReflectionTestUtils.setField(
            health, "maxSampleAgeNanos", java.time.Duration.ofSeconds(5).toNanos());
        PaymentPostgresFixture.psql(true, "select pg_wal_replay_resume()");
      }
    }
  }

  @Test
  void readerSqlTimeoutAndRecoveryConflictNeverRetryWriter() throws Exception {
    var health =
        context.getBean(com.flashticket.payment_service.config.PaymentReportReplicaHealth.class);
    for (String fault : new String[] {"permission", "timeout", "recovery-conflict"}) {
      UUID eventId = UUID.randomUUID();
      PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
      PaymentPostgresFixture.awaitReplay();
      long conflictsBefore =
          Long.parseLong(
              PaymentPostgresFixture.psql(
                  true,
                  "select confl_lock from pg_stat_database_conflicts where datname='payment_db'"));
      if (fault.equals("permission")) {
        PaymentPostgresFixture.psql(
            false, "revoke select on payment_schema.tbl_refunds from payment_ha_reader");
        PaymentPostgresFixture.awaitReplay();
      }
      ReflectionTestUtils.invokeMethod(health, "refresh");
      var transformed = new java.util.concurrent.atomic.AtomicBoolean();
      try {
        var observations =
            PaymentPostgresFixture.inspectRead(
                () -> {
                  PaymentPostgresFixture.SQL_TRANSFORM.set(
                      sql -> {
                        if (fault.equals("timeout") && transformed.compareAndSet(false, true))
                          return "select pg_sleep(12)";
                        if (fault.equals("recovery-conflict")
                            && sql.contains("tbl_event_payouts")
                            && transformed.compareAndSet(false, true)) {
                          try {
                            PaymentPostgresFixture.psql(
                                false,
                                "alter table payment_schema.tbl_event_finance_snapshots add column ha_conflict_marker integer");
                            org.awaitility.Awaitility.await()
                                .atMost(java.time.Duration.ofSeconds(15))
                                .untilAsserted(
                                    () ->
                                        assertThat(
                                                Long.parseLong(
                                                    PaymentPostgresFixture.psql(
                                                        true,
                                                        "select confl_lock from pg_stat_database_conflicts where datname='payment_db'")))
                                            .isGreaterThan(conflictsBefore));
                          } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                          }
                        }
                        return sql;
                      });
                  String path =
                      fault.equals("recovery-conflict")
                          ? "/api/v1/events/" + eventId + "/reconciliation"
                          : "/api/v1/admin/refunds";
                  return mvc.perform(
                          get(path)
                              .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                      .andExpect(status().isInternalServerError())
                      .andReturn();
                });
        assertThat(observations).isNotEmpty();
        for (var sql : observations)
          assertThat(sql.get("replica")).as(fault + " must not retry writer").isEqualTo(true);
        assertThat(health.tryBeginRead()).isFalse();
      } finally {
        PaymentPostgresFixture.SQL_TRANSFORM.remove();
        PaymentPostgresFixture.psql(
            false,
            "grant select on payment_schema.tbl_refunds to payment_ha_reader; alter table payment_schema.tbl_event_finance_snapshots drop column if exists ha_conflict_marker");
        PaymentPostgresFixture.awaitReplay();
      }
      ReflectionTestUtils.invokeMethod(health, "refresh");
      assertThat(health.tryBeginRead()).isTrue();
      health.finishRead();
      mvc.perform(
              get("/api/v1/admin/refunds")
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
          .andExpect(status().isOk());
    }
  }
}
