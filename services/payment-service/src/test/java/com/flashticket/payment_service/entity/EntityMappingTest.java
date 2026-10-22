package com.flashticket.payment_service.entity;

import static com.flashticket.payment_service.entity.PaymentEntityFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashticket.payment_service.entity.id.*;
import jakarta.persistence.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"mapping-test", "local-foundation"})
class EntityMappingTest {
  @Autowired EntityManager em;
  @Autowired javax.sql.DataSource datasource;
  @Autowired org.springframework.core.env.Environment environment;

  @Test
  void mappingProfileUsesRealDatasourceAndLocalFoundationDisablesListeners() throws Exception {
    try (var connection = datasource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
      assertThat(connection.getCatalog()).isEqualTo("payment_db");
    }
    assertThat(
            environment.getProperty("spring.rabbitmq.listener.simple.auto-startup", Boolean.class))
        .isFalse();
    assertThat(
            environment.getProperty("spring.rabbitmq.listener.direct.auto-startup", Boolean.class))
        .isFalse();
    assertThat(environment.getProperty("eureka.client.enabled", Boolean.class)).isFalse();
  }

  @Test
  void mapsAllThirteenPaymentTables() {
    assertThat(em.getMetamodel().getEntities()).hasSize(13);
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void persistsAllTablesAndNavigatesCompositeRelationshipsBothWays() {
    var f = new PaymentEntityFixtures(em);
    em.clear();
    var s = em.find(OrderPaymentSnapshot.class, f.snapshot.getOrderId());
    assertThat(s.getAttempts()).hasSize(1);
    var a = s.getAttempts().getFirst();
    assertThat(a.getOrderSnapshot().getOrderId()).isEqualTo(s.getOrderId());
    assertThat(a.getCharges()).hasSize(2);
    var c = em.find(Charge.class, f.charge.getId());
    assertThat(c.getAttempt().getId()).isEqualTo(a.getId());
    assertThat(s.getConfirmation().getCharge().getId()).isEqualTo(c.getId());
    assertThat(c.getConfirmation().getOrderSnapshot().getOrderId()).isEqualTo(s.getOrderId());
    assertThat(c.getRefund().getCharge().getId()).isEqualTo(c.getId());
    assertThat(c.getRefund().getAttempts())
        .hasSize(2)
        .allSatisfy(ra -> assertThat(ra.getRefund().getId()).isEqualTo(f.refund.getId()));
    assertThat(s.getSaga().getCharge().getId()).isEqualTo(c.getId());
    assertThat(c.getSagas()).hasSize(1);
    var run = em.find(EventRefundRun.class, f.run.getId());
    assertThat(run.getItems()).hasSize(2);
    var item = em.find(EventRefundItem.class, new EventRefundItemId(run.getId(), s.getOrderId()));
    assertThat(item.getRun().getId()).isEqualTo(run.getId());
    assertThat(item.getOrderSnapshot().getOrderId()).isEqualTo(s.getOrderId());
    assertThat(item.getConfirmation().getChargeId()).isEqualTo(c.getId());
    assertThat(item.getRefund().getId()).isEqualTo(f.refund.getId());
    assertThat(s.getRefundItems()).hasSize(1);
    assertThat(c.getConfirmation().getRefundItems()).hasSize(1);
    assertThat(c.getRefund().getItems()).hasSize(1);
    assertThat(run.getItems())
        .filteredOn(i -> i.getConfirmedChargeId() == null)
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.getConfirmation()).isNull();
              assertThat(i.getRefund()).isNull();
            });
    var finance = em.find(EventFinanceSnapshot.class, s.getEventId());
    assertThat(finance.getOrganizerSubject()).isNull();
    assertThat(finance.getCancelledAt()).isEqualTo(NOW);
    assertThat(s.getPurchaseSnapshot().get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(s.getExpectedAmount()).isEqualByComparingTo("10");
    assertThat(em.createQuery("from InboxMessage", InboxMessage.class).getResultList())
        .isNotEmpty();
    run.getItems().clear();
    em.flush();
    em.clear();
    assertThat(em.find(EventRefundRun.class, run.getId()).getItems()).hasSize(2);
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void databaseRejectsMismatchedCompositeAmount() {
    var f = new PaymentEntityFixtures(em);
    var invalid =
        make(
            PaymentAttempt.class,
            "orderId",
            f.snapshot.getOrderId(),
            "expectedAmount",
            new BigDecimal("11"),
            "state",
            com.flashticket.payment_service.entity.enums.PaymentAttemptState.FAILED,
            "finishedAt",
            NOW);
    assertSqlState(
        () -> {
          em.persist(invalid);
          em.flush();
        },
        "23503");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"provider", "confirmationAmount", "refundUnique", "unfinishedAttempt"})
  @org.springframework.transaction.annotation.Transactional
  void enforcesFinancialForeignKeysAndUniqueness(String scenario) {
    var f = new PaymentEntityFixtures(em);
    Object invalid =
        switch (scenario) {
          case "provider" ->
              make(
                  Charge.class,
                  "attemptId",
                  f.attempt.getId(),
                  "orderId",
                  f.snapshot.getOrderId(),
                  "provider",
                  "wrong-provider");
          case "confirmationAmount" ->
              make(
                  Refund.class,
                  "chargeId",
                  f.charge.getId(),
                  "orderId",
                  f.snapshot.getOrderId(),
                  "amount",
                  new BigDecimal("9"));
          case "refundUnique" ->
              make(Refund.class, "chargeId", f.charge.getId(), "orderId", f.snapshot.getOrderId());
          default -> make(PaymentAttempt.class, "orderId", f.snapshot.getOrderId());
        };
    // The amount test uses a different charge without an existing refund to isolate the FK.
    if (scenario.equals("confirmationAmount")) {
      var charge =
          make(Charge.class, "attemptId", f.attempt.getId(), "orderId", f.snapshot.getOrderId());
      em.persist(charge);
      em.flush();
      ((Refund) invalid).setChargeId(charge.getId());
    }
    assertSqlState(
        () -> {
          em.persist(invalid);
          em.flush();
        },
        scenario.equals("provider") || scenario.equals("confirmationAmount") ? "23503" : "23505");
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void roundTripsNineteenDigitMoneyAndMicrosecondTime() {
    var s =
        make(
            OrderPaymentSnapshot.class,
            "expectedAmount",
            new BigDecimal("9999999999999999999"),
            "receivedAt",
            NOW.plusNanos(123456000));
    em.persist(s);
    em.flush();
    em.clear();
    var loaded = em.find(OrderPaymentSnapshot.class, s.getOrderId());
    assertThat(loaded.getExpectedAmount()).isEqualByComparingTo("9999999999999999999");
    assertThat(loaded.getReceivedAt()).isEqualTo(NOW.plusNanos(123456000));
  }

  private static void assertSqlState(Runnable action, String expected) {
    try {
      action.run();
      throw new AssertionError("Expected SQL constraint failure");
    } catch (RuntimeException ex) {
      Throwable cause = ex;
      while (cause.getCause() != null) cause = cause.getCause();
      assertThat(cause).isInstanceOf(java.sql.SQLException.class);
      assertThat(((java.sql.SQLException) cause).getSQLState()).isEqualTo(expected);
    }
  }

  @Autowired EntityManagerFactory emf;

  @Test
  void concurrentUpdatesDetectStaleVersion() {
    try (var first = emf.createEntityManager();
        var second = emf.createEntityManager()) {
      first.getTransaction().begin();
      var f = new PaymentEntityFixtures(first);
      first.getTransaction().commit();
      first.clear();
      first.getTransaction().begin();
      second.getTransaction().begin();
      var one = first.find(PaymentAttempt.class, f.attempt.getId());
      var two = second.find(PaymentAttempt.class, f.attempt.getId());
      one.setPaymentUrl("https://fixture.example/first");
      first.getTransaction().commit();
      two.setPaymentUrl("https://fixture.example/second");
      assertThatThrownBy(() -> second.getTransaction().commit())
          .isInstanceOf(RollbackException.class);
    }
  }
}
