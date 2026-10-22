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
    int versions = 0;
    int embeddedIds = 0;
    int owningJoins = 0;
    for (var entity : em.getMetamodel().getEntities()) {
      var type = entity.getJavaType();
      var table = type.getAnnotation(Table.class);
      assertThat(table.schema()).isEqualTo("payment_schema");
      assertThat(table.name()).startsWith("tbl_");
      for (var field : type.getDeclaredFields()) {
        if (field.isAnnotationPresent(Version.class)) versions++;
        if (field.isAnnotationPresent(EmbeddedId.class)) embeddedIds++;
        var many = field.getAnnotation(ManyToOne.class);
        var one = field.getAnnotation(OneToOne.class);
        var collection = field.getAnnotation(OneToMany.class);
        if (many != null) {
          assertThat(many.fetch()).isEqualTo(FetchType.LAZY);
          assertThat(many.cascade()).isEmpty();
        }
        if (one != null) {
          assertThat(one.fetch()).isEqualTo(FetchType.LAZY);
          assertThat(one.cascade()).isEmpty();
          assertThat(one.orphanRemoval()).isFalse();
        }
        if (collection != null) {
          assertThat(collection.fetch()).isEqualTo(FetchType.LAZY);
          assertThat(collection.cascade()).isEmpty();
          assertThat(collection.orphanRemoval()).isFalse();
          assertThat(collection.mappedBy()).isNotBlank();
        }
        var joins = field.getAnnotationsByType(JoinColumn.class);
        if (joins.length > 0) owningJoins++;
        for (var join : joins) {
          assertThat(join.insertable()).isFalse();
          assertThat(join.updatable()).isFalse();
          assertThat(join.referencedColumnName()).isNotBlank();
        }
      }
    }
    assertThat(versions).isEqualTo(5);
    assertThat(embeddedIds).isEqualTo(2);
    assertThat(owningJoins).isEqualTo(12);
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
    assertThat(s.getPurchaseSnapshot()).isEqualTo(f.snapshot.getPurchaseSnapshot());
    assertThat(em.find(OutboxMessage.class, f.outbox.getMessageId()).getPayload())
        .isEqualTo(f.outbox.getPayload());
    assertThat(em.find(InboxMessage.class, f.inbox.getId()).getResponsePayload())
        .isEqualTo(f.inbox.getResponsePayload());
    var otherInbox =
        make(
            InboxMessage.class,
            "id",
            new InboxMessageId("other-consumer", f.inbox.getId().getMessageId()));
    em.persist(otherInbox);
    em.flush();
    assertThat(em.find(InboxMessage.class, otherInbox.getId()).getPayloadHash())
        .isNotEqualTo(f.inbox.getPayloadHash());
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
      strings = {
        "provider",
        "refundAmount",
        "refundUnique",
        "unfinishedAttempt",
        "confirmationAmount",
        "confirmationUnique",
        "crossOrder",
        "currency"
      })
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
          case "refundAmount" ->
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
          case "confirmationAmount", "confirmationUnique" ->
              make(
                  PaymentConfirmation.class,
                  "chargeId",
                  f.charge.getId(),
                  "amount",
                  scenario.equals("confirmationAmount") ? new BigDecimal("9") : BigDecimal.TEN);
          case "crossOrder" ->
              make(Charge.class, "attemptId", f.attempt.getId(), "orderId", UUID.randomUUID());
          case "currency" ->
              make(PaymentAttempt.class, "orderId", f.snapshot.getOrderId(), "currency", "USD");
          default -> make(PaymentAttempt.class, "orderId", f.snapshot.getOrderId());
        };
    // The amount test uses a different charge without an existing refund to isolate the FK.
    if (scenario.equals("refundAmount")) {
      var charge =
          make(Charge.class, "attemptId", f.attempt.getId(), "orderId", f.snapshot.getOrderId());
      em.persist(charge);
      em.flush();
      ((Refund) invalid).setChargeId(charge.getId());
    }
    if (scenario.startsWith("confirmation")) {
      var other = make(OrderPaymentSnapshot.class);
      em.persist(other);
      em.flush();
      ((PaymentConfirmation) invalid).setOrderId(other.getOrderId());
      if (scenario.equals("confirmationAmount")) {
        var attempt = make(PaymentAttempt.class, "orderId", other.getOrderId());
        em.persist(attempt);
        em.flush();
        var charge =
            make(Charge.class, "attemptId", attempt.getId(), "orderId", other.getOrderId());
        em.persist(charge);
        em.flush();
        ((PaymentConfirmation) invalid).setChargeId(charge.getId());
      }
    }
    assertSqlState(
        () -> {
          em.persist(invalid);
          em.flush();
        },
        switch (scenario) {
          case "provider", "refundAmount", "confirmationAmount", "crossOrder" -> "23503";
          case "currency" -> "23514";
          default -> "23505";
        });
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
          .isInstanceOf(RollbackException.class)
          .hasCauseInstanceOf(OptimisticLockException.class);
    }
  }
}
