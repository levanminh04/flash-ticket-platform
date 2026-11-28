package com.flashticket.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashticket.payment_service.client.BookingPurchaseClient;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.FinanceConfiguration;
import com.flashticket.payment_service.dto.request.CreatePaymentRequest;
import com.flashticket.payment_service.entity.enums.RefundState;
import com.flashticket.payment_service.repository.RefundRepository;
import com.flashticket.payment_service.service.FinanceService;
import com.flashticket.payment_service.service.PaymentSagaService;
import com.flashticket.payment_service.service.PaymentService;
import com.flashticket.payment_service.service.RefundService;
import com.flashticket.payment_service.support.PaymentHaInstanceConfiguration;
import com.flashticket.payment_service.support.PaymentPostgresFixture;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
  void twoRefundApisUsePhysicalReplica() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID firstOrder = PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
    UUID secondOrder = PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
    var jdbc = context.getBean(JdbcTemplate.class);
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
          Timestamp.from(
              Instant.parse(
                  orderId.equals(firstOrder) ? "2026-11-01T01:00:00Z" : "2026-11-01T02:00:00Z")),
          orderId);
      jdbc.update(
          "insert into payment_schema.tbl_event_refund_items(run_id,event_id,order_id,confirmed_charge_id,refund_id,state) select ?,?,order_id,id,?,'NEEDS_ATTENTION' from payment_schema.tbl_charges where order_id=?",
          runId,
          eventId,
          refundId,
          orderId);
      for (int attempt = 1; attempt <= 2; attempt++) {
        var startedAt = Instant.parse("2026-11-02T00:00:00Z").plusSeconds(attempt);
        jdbc.update(
            "insert into payment_schema.tbl_refund_attempts(id,refund_id,request_key,started_at,finished_at,outcome) values (?,?,?,?,?,?)",
            UUID.randomUUID(),
            refundId,
            UUID.randomUUID().toString(),
            Timestamp.from(startedAt),
            Timestamp.from(startedAt.plusSeconds(1)),
            attempt == 1 ? "OLDER_FAILURE" : "LATEST_FAILURE");
      }
    }
    PaymentPostgresFixture.awaitReplay();
    assertThat(context.getBean("dataSource")).isInstanceOf(LazyConnectionDataSourceProxy.class);
    assertThat(context.getBeansOfType(EntityManagerFactory.class)).hasSize(1);
    assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
    assertThat(context.getBeansOfType(HikariDataSource.class))
        .containsOnlyKeys("writeDs", "readDs");
    for (String path :
        new String[] {
          "/api/v1/admin/refunds?size=1", "/api/v1/admin/events/" + eventId + "/refund-progress"
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
                } else {
                  assertThat(payload.path("selectedOrderCount").asLong()).isEqualTo(2);
                  assertThat(payload.path("needsAttentionCount").asLong()).isEqualTo(2);
                  assertThat(payload.path("pendingCount").asLong()).isZero();
                }
                return response;
              });
      assertThat(observations).as(path).isNotEmpty();
      Object pid = observations.getFirst().get("pid");
      boolean countObserved = false;
      boolean attemptObserved = false;
      for (var observation : observations) {
        String sql = observation.get("sql").toString().toLowerCase(java.util.Locale.ROOT);
        countObserved |= sql.contains("count(");
        attemptObserved |= sql.contains("tbl_refund_attempts");
        assertThat(observation.get("replica")).isEqualTo(true);
        assertThat(observation.get("readOnly")).isEqualTo("on");
        assertThat(observation.get("pid")).isEqualTo(pid);
      }
      if (path.startsWith("/api/v1/admin/refunds")) {
        assertThat(countObserved).as("Page count cùng connection reader").isTrue();
        assertThat(attemptObserved).as("Latest attempt cùng connection reader").isTrue();
      }
    }
    mvc.perform(
            get("/api/v1/admin/refunds")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void transactionFlagsAndNoTransactionChoosePhysicalEndpoints() throws Exception {
    assertTransactionRole(true, true);
    assertTransactionRole(false, false);
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(
            context
                .getBean(JdbcTemplate.class)
                .queryForObject("select pg_is_in_recovery()", Boolean.class))
        .isFalse();
    // Repository tự mở readOnly TX; không gọi đây là SQL ngoài transaction.
    assertPhysicalRole(true, () -> context.getBean(RefundRepository.class).count());
  }

  @Test
  void outerWriteTransactionKeepsNestedReadsOnPrimary() throws Exception {
    var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    assertPhysicalRole(
        false,
        () ->
            transaction.execute(
                status -> {
                  context.getBean(RefundService.class).getRefunds(RefundState.FAILED, 0, 20);
                  assertThat(
                          context
                              .getBean(JdbcTemplate.class)
                              .queryForObject("select pg_is_in_recovery()", Boolean.class))
                      .isFalse();
                  return null;
                }));
  }

  @Test
  void paymentAndWorkerReadsStayOnPrimaryDuringReplicaLag() throws Exception {
    var payments = context.getBean(PaymentService.class);
    var finance = context.getBean(FinanceService.class);
    var refunds = context.getBean(RefundService.class);
    var sagas = context.getBean(PaymentSagaService.class);
    var jdbc = context.getBean(JdbcTemplate.class);
    PaymentPostgresFixture.psql(true, "select pg_wal_replay_pause()");
    try {
      UUID eventId = UUID.randomUUID();
      UUID confirmedOrder =
          PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
      var purchase = PaymentPostgresFixture.purchase(context, eventId, "buyer-1");
      org.mockito.Mockito.when(
              context
                  .getBean(BookingPurchaseClient.class)
                  .preparePurchaseSnapshot(purchase.orderId(), "buyer-1"))
          .thenReturn(purchase);
      var started =
          payments.createPayment(
              new CreatePaymentRequest(purchase.orderId(), null),
              new MockHttpServletRequest(),
              "buyer-1",
              UUID.randomUUID());
      var ipn = PaymentPostgresFixture.signedIpn(started);
      assertPhysicalRole(
          false,
          () -> {
            assertThat(payments.handleIPN(ipn)).contains("\"RspCode\":\"00\"");
            return null;
          });
      assertPhysicalRole(
          false,
          () -> {
            assertThat(payments.handleReturn(ipn).attemptId()).isEqualTo(started.attemptId());
            return null;
          });
      assertPhysicalRole(
          false,
          () -> {
            assertThat(payments.buildAppReturnUri(ipn).toString())
                .contains(started.attemptId().toString());
            return null;
          });
      assertPhysicalRole(
          false,
          () -> {
            assertThat(
                    payments
                        .getPayment(purchase.orderId(), UUID.randomUUID(), "buyer-1")
                        .latestAttempt()
                        .attemptId())
                .isEqualTo(started.attemptId());
            return null;
          });
      assertPhysicalRole(
          false,
          () -> {
            var summary = finance.getFinancialSummary(eventId);
            assertThat(summary.hasCollectedOrders()).isTrue();
            assertThat(summary.netAmount()).isEqualTo("92500");
            return null;
          });
      assertPhysicalRole(
          false,
          () -> {
            assertThat(sagas.findRecoverableOrderIds(Instant.now().plusSeconds(120), 20))
                .contains(purchase.orderId());
            return null;
          });
      UUID pending = UUID.randomUUID();
      jdbc.update(
          "insert into payment_schema.tbl_refunds(id,charge_id,order_id,amount,currency,reason,state) select ?,id,order_id,amount,currency,'EVENT_CANCELLED','PENDING' from payment_schema.tbl_charges where order_id=?",
          pending,
          confirmedOrder);
      assertPhysicalRole(
          false,
          () -> {
            assertThat(refunds.findRefundsToSend(20)).contains(pending);
            return null;
          });
      jdbc.update("update payment_schema.tbl_refunds set state='PROCESSING' where id=?", pending);
      jdbc.update(
          "insert into payment_schema.tbl_refund_attempts(id,refund_id,request_key,started_at) values (?,?,?,?)",
          UUID.randomUUID(),
          pending,
          UUID.randomUUID().toString(),
          Timestamp.from(Instant.now().minusSeconds(60)));
      assertPhysicalRole(
          false,
          () -> {
            assertThat(refunds.findRefundsToPoll(Instant.now(), 20)).contains(pending);
            return null;
          });
      UUID runId = UUID.randomUUID();
      jdbc.update(
          "insert into payment_schema.tbl_event_refund_runs(id,event_id,source_cancellation_id,selection_complete) values (?,?,?,false)",
          runId,
          eventId,
          UUID.randomUUID());
      assertPhysicalRole(
          false,
          () -> {
            assertThat(refunds.findEventRefundRunsToSelect(20)).contains(runId);
            return null;
          });
    } finally {
      PaymentPostgresFixture.psql(true, "select pg_wal_replay_resume()");
      PaymentPostgresFixture.awaitReplay();
    }
  }

  @Test
  void reconciliationChecksCurrentOwnerOnPrimaryDuringLag() throws Exception {
    UUID eventId = UUID.randomUUID();
    PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "owner-a");
    PaymentPostgresFixture.awaitReplay();
    PaymentPostgresFixture.psql(true, "select pg_wal_replay_pause()");
    try {
      context
          .getBean(FinanceService.class)
          .recordFinanceConfiguration(
              new Envelope<>(
                  UUID.randomUUID(),
                  "event.finance-configured.v1",
                  1,
                  eventId,
                  "2",
                  Instant.now(),
                  UUID.randomUUID(),
                  null,
                  null,
                  new FinanceConfiguration(
                      eventId, "owner-b", Instant.now().minusSeconds(3600), "0.075", "2", "VND")));
      assertPhysicalRole(
          false,
          () ->
              mvc.perform(
                      get("/api/v1/events/" + eventId + "/reconciliation")
                          .with(
                              jwt()
                                  .jwt(token -> token.subject("owner-a"))
                                  .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                  .andExpect(status().isNotFound())
                  .andReturn());
      assertPhysicalRole(
          false,
          () ->
              mvc.perform(
                      get("/api/v1/events/" + eventId + "/reconciliation")
                          .with(
                              jwt()
                                  .jwt(token -> token.subject("owner-b"))
                                  .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                  .andExpect(status().isOk())
                  .andReturn());
    } finally {
      PaymentPostgresFixture.psql(true, "select pg_wal_replay_resume()");
      PaymentPostgresFixture.awaitReplay();
    }
  }

  @Test
  void replicaFailureDoesNotRetryPrimary() throws Exception {
    UUID eventId = UUID.randomUUID();
    PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
    var jdbc = context.getBean(JdbcTemplate.class);
    jdbc.update(
        "insert into payment_schema.tbl_event_refund_runs(id,event_id,source_cancellation_id,selection_complete) values (?,?,?,false)",
        UUID.randomUUID(),
        eventId,
        UUID.randomUUID());
    PaymentPostgresFixture.awaitReplay();
    // Witness lấy trước câu SQL lỗi; mọi attempt quan sát được vẫn chỉ ở standby.
    for (String path :
        new String[] {
          "/api/v1/admin/refunds", "/api/v1/admin/events/" + eventId + "/refund-progress"
        }) {
      assertPhysicalRole(
          true,
          () -> {
            PaymentPostgresFixture.SQL_TRANSFORM.set(
                sql -> {
                  try {
                    PaymentPostgresFixture.psql(
                        true,
                        "select pg_terminate_backend(pid) from pg_stat_activity where datname='payment_db' and backend_type='client backend' and pid<>pg_backend_pid()");
                  } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                  }
                  return sql;
                });
            return mvc.perform(
                    get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isInternalServerError())
                .andReturn();
          });
    }
    var replica =
        (org.testcontainers.containers.GenericContainer<?>)
            ReflectionTestUtils.getField(PaymentPostgresFixture.class, "standby");
    replica.getDockerClient().pauseContainerCmd(replica.getContainerId()).exec();
    try {
      // Không dùng SQL witness khi endpoint mất kết nối: witness không được che lỗi HTTP thật.
      for (String path :
          new String[] {
            "/api/v1/admin/refunds", "/api/v1/admin/events/" + eventId + "/refund-progress"
          }) {
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isInternalServerError());
      }
      assertThat(
              context
                  .getBean(
                      "dbHealthContributor",
                      org.springframework.boot.jdbc.health.DataSourceHealthIndicator.class)
                  .health()
                  .getStatus()
                  .getCode())
          .isEqualTo("UP");
      assertPhysicalRole(
          false, () -> context.getBean(FinanceService.class).getFinancialSummary(eventId));
    } finally {
      replica.getDockerClient().unpauseContainerCmd(replica.getContainerId()).exec();
      context
          .getBean("readDs", HikariDataSource.class)
          .getHikariPoolMXBean()
          .softEvictConnections();
    }
    mvc.perform(
            get("/api/v1/admin/refunds")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk());
  }

  @Test
  void pausedReplicaMayReturnStaleReportsWithoutAHealthGate() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID orderId = PaymentPostgresFixture.seedConfirmedOrder(context, eventId, "organizer-1");
    var jdbc = context.getBean(JdbcTemplate.class);
    UUID refundId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    jdbc.update(
        "insert into payment_schema.tbl_refunds(id,charge_id,order_id,amount,currency,reason,state) select ?,id,order_id,amount,currency,'EVENT_CANCELLED','FAILED' from payment_schema.tbl_charges where order_id=?",
        refundId,
        orderId);
    jdbc.update(
        "insert into payment_schema.tbl_event_refund_runs(id,event_id,source_cancellation_id,selection_complete) values (?,?,?,false)",
        runId,
        eventId,
        UUID.randomUUID());
    PaymentPostgresFixture.awaitReplay();
    PaymentPostgresFixture.psql(true, "select pg_wal_replay_pause()");
    try {
      jdbc.update(
          "update payment_schema.tbl_refunds set state='SUCCEEDED',completed_at=now() where id=?",
          refundId);
      jdbc.update(
          "update payment_schema.tbl_event_refund_runs set selection_complete=true where id=?",
          runId);
      assertPhysicalRole(
          true,
          () -> {
            var page = context.getBean(RefundService.class).getRefunds(RefundState.FAILED, 0, 20);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.items().getFirst().state()).isEqualTo(RefundState.FAILED);
            return null;
          });
      assertPhysicalRole(
          true,
          () -> {
            assertThat(
                    context
                        .getBean(RefundService.class)
                        .getEventRefundProgress(eventId)
                        .selectionComplete())
                .isFalse();
            return null;
          });
      assertThat(
              jdbc.queryForObject(
                  "select state::text from payment_schema.tbl_refunds where id=?",
                  String.class,
                  refundId))
          .isEqualTo("SUCCEEDED");
    } finally {
      PaymentPostgresFixture.psql(true, "select pg_wal_replay_resume()");
      PaymentPostgresFixture.awaitReplay();
    }
  }

  private void assertPhysicalRole(boolean expectedReplica, Callable<?> operation) throws Exception {
    var observations =
        PaymentPostgresFixture.inspectRead(
            () -> {
              org.assertj.core.api.Assertions.assertThatCode(operation::call)
                  .doesNotThrowAnyException();
              return null;
            });
    assertThat(observations).as("SQL JPA thật phải được quan sát").isNotEmpty();
    for (var observation : observations) {
      assertThat(observation.get("replica"))
          .as(observation.get("sql").toString())
          .isEqualTo(expectedReplica);
    }
  }

  private void assertTransactionRole(boolean readOnly, boolean expectedReplica) throws Exception {
    var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    transaction.setReadOnly(readOnly);
    transaction.executeWithoutResult(
        status ->
            assertThat(
                    context
                        .getBean(JdbcTemplate.class)
                        .queryForObject("select pg_is_in_recovery()", Boolean.class))
                .isEqualTo(expectedReplica));
  }
}
