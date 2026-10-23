package com.flashticket.payment_service.repository;

import static com.flashticket.payment_service.entity.PaymentReadFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.payment_service.dto.message.StartPaymentResultState;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import com.flashticket.payment_service.service.payment.PaymentQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles({"mapping-test", "local-foundation"})
class PaymentReadRepositoriesTest {
  @Autowired EntityManager em;
  @MockitoSpyBean OrderPaymentSnapshotRepository snapshots;
  @Autowired PaymentAttemptRepository attempts;
  @Autowired PaymentConfirmationRepository confirmations;
  @Autowired RefundRepository refunds;
  @Autowired InboxMessageRepository inbox;
  @Autowired PaymentQueryService payments;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManagerFactory emf;

  @Test
  @Transactional
  void accessProjectionDoesNotLoadEntityGraph() {
    var s = snapshot(em, "buyer");
    em.flush();
    em.clear();
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    try {
      assertThat(snapshots.findAccessByOrderId(s.getOrderId())).isPresent();
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
      assertThat(statistics.getEntityLoadCount()).isZero();
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  @Test
  void querySeesOneCommittedSnapshotDuringConcurrentInsertion() throws Exception {
    var tx = new TransactionTemplate(transactions);
    var order = tx.execute(s -> snapshot(em, "buyer").getOrderId());
    var read = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    // Spring Data query methods have no Java body to callRealMethod; execute the equivalent
    // scalar read on the transaction-bound JDBC connection, then pause that real DB snapshot.
    doAnswer(
            inv -> {
              var owner =
                  jdbc.queryForObject(
                      "select buyer_subject from payment_schema.tbl_order_payment_snapshots where order_id=?",
                      String.class,
                      order);
              var access = mock(OrderPaymentSnapshotRepository.OrderPaymentAccess.class);
              when(access.getOrderId()).thenReturn(order);
              when(access.getBuyerSubject()).thenReturn(owner);
              when(access.getExpiresAt()).thenReturn(TIME.plusSeconds(600));
              read.countDown();
              if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("read barrier timed out");
              return java.util.Optional.of(access);
            })
        .when(snapshots)
        .findAccessByOrderId(order);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var future = pool.submit(() -> payments.getPayment(order, null, "buyer"));
      try {
        if (!read.await(10, TimeUnit.SECONDS)) {
          if (future.isDone()) future.get();
          throw new IllegalStateException("Service did not reach repository read barrier");
        }
        tx.executeWithoutResult(
            s -> attempt(em, order, UUID.randomUUID(), PaymentAttemptState.PENDING));
      } finally {
        release.countDown();
      }
      assertThat(future.get(10, TimeUnit.SECONDS).latestAttempt()).isNull();
      assertThat(payments.getPayment(order, null, "buyer").latestAttempt()).isNotNull();
    }
  }

  @Test
  void getDoesNotChangeAnyPaymentTable() {
    var tx = new TransactionTemplate(transactions);
    var order = tx.execute(s -> snapshot(em, "buyer").getOrderId());
    var before = contents();
    payments.getPayment(order, UUID.randomUUID(), "buyer");
    assertThat(contents()).isEqualTo(before);
  }

  Map<String, String> contents() {
    var rows = new LinkedHashMap<String, String>();
    for (String table :
        new String[] {
          "order_payment_snapshots",
          "payment_attempts",
          "charges",
          "payment_confirmations",
          "refunds",
          "refund_attempts",
          "payment_sagas",
          "event_finance_snapshots",
          "event_refund_runs",
          "event_refund_items",
          "event_payouts",
          "inbox_messages",
          "outbox_messages"
        })
      rows.put(
          table,
          jdbc.queryForObject(
              "select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb)::text from payment_schema.tbl_"
                  + table
                  + " t",
              String.class));
    return rows;
  }

  @Test
  @Transactional
  void readsOnlyRequestedOrderAndSortsAttemptTies() {
    var s = snapshot(em, "buyer");
    var other = snapshot(em, "other");
    var small = UUID.fromString("10000000-0000-0000-0000-000000000010");
    var large = UUID.fromString("10000000-0000-0000-0000-000000000020");
    attempt(em, s.getOrderId(), large, PaymentAttemptState.FAILED);
    attempt(em, s.getOrderId(), small, PaymentAttemptState.FAILED);
    attempt(em, other.getOrderId(), UUID.randomUUID(), PaymentAttemptState.PENDING);
    em.flush();
    em.clear();
    assertThat(snapshots.findAccessByOrderId(s.getOrderId())).isPresent();
    var access = snapshots.findAccessByOrderId(s.getOrderId()).orElseThrow();
    assertThat(access.getBuyerSubject()).isEqualTo("buyer");
    assertThat(access.getExpiresAt()).isEqualTo(TIME.plusSeconds(600));
    assertThat(
            attempts
                .findFirstByOrderIdOrderByStartedAtDescIdDesc(s.getOrderId())
                .orElseThrow()
                .getId())
        .isEqualTo(large);
    assertThat(attempts.findByIdAndOrderId(large, other.getOrderId())).isEmpty();
    assertThat(snapshots.findAccessByOrderId(UUID.randomUUID())).isEmpty();
  }

  @Test
  @Transactional
  void readsConfirmationRefundsAndCompositeInboxIdentity() {
    var s = snapshot(em, "buyer");
    var a = attempt(em, s.getOrderId(), UUID.randomUUID(), PaymentAttemptState.SUCCEEDED);
    var c = charge(em, a);
    confirm(em, c);
    var c2 = charge(em, a);
    var small = UUID.fromString("20000000-0000-0000-0000-000000000010");
    var large = UUID.fromString("20000000-0000-0000-0000-000000000020");
    refund(em, c, large);
    refund(em, c2, small);
    var request = UUID.randomUUID();
    em.persist(inbox(request, s.getOrderId(), StartPaymentResultState.CREATED, a.getId()));
    em.flush();
    em.clear();
    assertThat(confirmations.findChargeIdByOrderId(s.getOrderId())).contains(c.getId());
    assertThat(confirmations.findChargeIdByOrderId(UUID.randomUUID())).isEmpty();
    assertThat(refunds.findAllByOrderIdOrderByCreatedAtAscIdAsc(s.getOrderId()))
        .extracting(r -> r.getId())
        .containsExactly(small, large);
    assertThat(refunds.findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID.randomUUID())).isEmpty();
    assertThat(inbox.findById(new InboxMessageId("payment.start-requested.v1", request)))
        .isPresent();
    assertThat(inbox.findById(new InboxMessageId("other", request))).isEmpty();
  }
}
