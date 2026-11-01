package com.flashticket.payment_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.payment_service.client.BookingPurchaseClient;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.request.CreatePaymentRequest;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.entity.enums.*;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.messaging.PaymentMessageListener;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.util.VNPayUtil;
import com.flashticket.payment_service.worker.PaymentWorker;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("payment-flow-test")
class PaymentFlowIntegrationTest {
  @MockitoBean BookingPurchaseClient bookingClient;
  @Autowired PaymentService payments;
  @Autowired PaymentSagaService sagas;
  @Autowired MessagingService messaging;
  @Autowired PaymentMessageListener listener;
  @Autowired PaymentWorker worker;
  @Autowired JdbcTemplate jdbc;
  @Autowired ContractCodec codec;
  @Autowired JsonMapper json;
  @Autowired TransactionTemplate tx;
  @Autowired PaymentSagaRepository sagaRepository;
  @Autowired PaymentAttemptRepository attempts;
  @Autowired ChargeRepository charges;
  @Autowired OutboxMessageRepository outbox;
  @Autowired RefundRepository refunds;
  @Autowired RabbitTemplate rabbitTemplate;
  PurchaseSnapshot purchase;
  UUID orderId;
  UUID requestKey;
  UUID issuanceId;
  Map<String, String> ipn;

  @BeforeEach
  void setUp() {
    jdbc.execute(
        "TRUNCATE payment_schema.tbl_order_payment_snapshots, payment_schema.tbl_event_finance_snapshots, payment_schema.tbl_outbox_messages, payment_schema.tbl_inbox_messages CASCADE");
    try {
      var node =
          (ObjectNode)
              json.readTree(
                  new ClassPathResource("contracts/purchase.json")
                      .getContentAsString(StandardCharsets.UTF_8));
      orderId = UUID.randomUUID();
      requestKey = UUID.randomUUID();
      issuanceId = UUID.randomUUID();
      node.put("orderId", orderId.toString())
          .put("sourceOrderVersion", "1")
          .put("subtotal", "100000")
          .put("discount", "0")
          .put("total", "100000")
          .put("frozenAt", Instant.now().minusSeconds(1).toString())
          .put("expiresAt", Instant.now().plusSeconds(600).toString());
      ((ObjectNode) node.withArray("items").get(0))
          .put("unitPrice", "100000")
          .put("lineSubtotal", "100000");
      purchase = codec.fromTree(node, PurchaseSnapshot.class);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    when(bookingClient.preparePurchaseSnapshot(orderId, "buyer-1"))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return purchase;
            });
    ipn = new TreeMap<>();
    ipn.put("vnp_TmnCode", "test");
    ipn.put("vnp_Amount", "10000000");
    ipn.put("vnp_ResponseCode", "00");
    ipn.put("vnp_TransactionStatus", "00");
    ipn.put("vnp_TransactionNo", UUID.randomUUID().toString());
    ipn.put("vnp_PayDate", "20261030010000");
  }

  @Test
  void storesSnapshotAndReusesAttemptWithoutRepricing() {
    var started =
        payments.createPayment(
            new CreatePaymentRequest(orderId, "203.0.113.5"),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    var replay =
        payments.createPayment(
            new CreatePaymentRequest(orderId, "198.51.100.1"),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    assertThat(replay).isEqualTo(started);
    assertThat(attempts.count()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select expected_amount from payment_schema.tbl_order_payment_snapshots where order_id=?",
                java.math.BigDecimal.class,
                orderId))
        .isEqualByComparingTo("100000");
    var changed = (ObjectNode) codec.toTree(purchase);
    changed.put("eventTitle", "Changed after freeze");
    purchase = codec.fromTree(changed, PurchaseSnapshot.class);
    assertThatThrownBy(
            () ->
                payments.createPayment(
                    new CreatePaymentRequest(orderId, null),
                    new MockHttpServletRequest(),
                    "buyer-1",
                    requestKey))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.code()).isEqualTo("PURCHASE_SNAPSHOT_CONFLICT"));
    assertThat(attempts.count()).isEqualTo(1);
  }

  @Test
  void serializesConcurrentPaymentRequests() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var ready = new CountDownLatch(2);
      var go = new CountDownLatch(1);
      var tasks = new ArrayList<Future<UUID>>();
      for (int i = 0; i < 2; i++)
        tasks.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return payments
                      .createPayment(
                          new CreatePaymentRequest(orderId, null),
                          new MockHttpServletRequest(),
                          "buyer-1",
                          requestKey)
                      .attemptId();
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      assertThat(tasks.get(0).get(15, TimeUnit.SECONDS))
          .isEqualTo(tasks.get(1).get(15, TimeUnit.SECONDS));
    }
    assertThat(attempts.count()).isEqualTo(1);
    assertThatThrownBy(
            () ->
                payments.createPayment(
                    new CreatePaymentRequest(orderId, null),
                    new MockHttpServletRequest(),
                    "buyer-1",
                    UUID.randomUUID()))
        .isInstanceOfSatisfying(
            PaymentException.class, ex -> assertThat(ex.code()).isEqualTo("PAYMENT_IN_PROGRESS"));
    var failed = attempts.findAll().getFirst();
    tx.executeWithoutResult(
        status -> {
          var stored = attempts.findForUpdateById(failed.getId()).orElseThrow();
          stored.setState(PaymentAttemptState.FAILED);
          stored.setFinishedAt(Instant.now());
          attempts.save(stored);
        });
    assertThat(
            payments
                .createPayment(
                    new CreatePaymentRequest(orderId, null),
                    new MockHttpServletRequest(),
                    "buyer-1",
                    requestKey)
                .attemptState())
        .isEqualTo(PaymentAttemptState.FAILED);
    assertThat(attempts.count()).isEqualTo(1);
    UUID secondKey = UUID.randomUUID();
    var second =
        payments.createPayment(
            new CreatePaymentRequest(orderId, null),
            new MockHttpServletRequest(),
            "buyer-1",
            secondKey);
    tx.executeWithoutResult(
        status -> {
          var stored = attempts.findForUpdateById(second.attemptId()).orElseThrow();
          stored.setState(PaymentAttemptState.UNKNOWN);
          attempts.save(stored);
        });
    assertThatThrownBy(
            () ->
                payments.createPayment(
                    new CreatePaymentRequest(orderId, null),
                    new MockHttpServletRequest(),
                    "buyer-1",
                    secondKey))
        .isInstanceOf(PaymentException.class);
    assertThat(attempts.count()).isEqualTo(2);
  }

  @Test
  void commitsChargeAndWaitingBookingBeforeWebhookResponse() {
    var started =
        payments.createPayment(
            new CreatePaymentRequest(orderId, null),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    ipn.put("vnp_TxnRef", started.attemptId().toString());
    ipn.put(
        "vnp_SecureHash",
        VNPayUtil.hmacSHA512(
            "fixture-secret",
            ipn.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining("&"))));
    jdbc.execute(
        "ALTER TABLE payment_schema.tbl_outbox_messages ADD CONSTRAINT gov156_reject_accept CHECK(message_type <> 'booking.accept-payment.v1')");
    try {
      assertThatThrownBy(() -> payments.handleIPN(ipn))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(charges.count()).isZero();
      assertThat(sagaRepository.count()).isZero();
      assertThat(outbox.count()).isZero();
      assertThat(attempts.findById(started.attemptId()).orElseThrow().getState())
          .isEqualTo(PaymentAttemptState.PENDING);
    } finally {
      jdbc.execute(
          "ALTER TABLE payment_schema.tbl_outbox_messages DROP CONSTRAINT gov156_reject_accept");
    }
    assertThat(payments.handleIPN(ipn)).contains("\"RspCode\":\"00\"");
    assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.WAITING_BOOKING);
    assertThat(charges.count()).isEqualTo(1);
    assertThat(outbox.count()).isEqualTo(1);
    assertThat(payments.handleIPN(ipn)).contains("\"RspCode\":\"02\"");
    assertThat(charges.count()).isEqualTo(1);
    ipn.remove("vnp_SecureHash");
    ipn.put("vnp_TransactionNo", UUID.randomUUID().toString());
    ipn.put(
        "vnp_SecureHash",
        VNPayUtil.hmacSHA512(
            "fixture-secret",
            ipn.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining("&"))));
    payments.handleIPN(ipn);
    assertThat(charges.count()).isEqualTo(2);
    assertThat(refunds.count()).isEqualTo(1);
    assertThat(outbox.count()).isEqualTo(1);
  }

  @Test
  void issuesTicketsOnlyAfterBookingAcceptance() {
    var started =
        payments.createPayment(
            new CreatePaymentRequest(orderId, null),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    var received =
        tx.execute(
            status -> {
              var attempt = attempts.findForUpdateById(started.attemptId()).orElseThrow();
              var charge =
                  charges.save(
                      com.flashticket.payment_service.entity.Charge.received(
                          attempt, "accept-test", Instant.now(), Instant.now()));
              sagas.recordSuccessfulCharge(charge);
              return charge;
            });
    assertThat(outbox.count()).isEqualTo(1);
    var decision =
        new Envelope<>(
            UUID.randomUUID(),
            "booking.payment-decision.v1",
            1,
            orderId,
            "1",
            Instant.now(),
            orderId,
            null,
            null,
            new PaymentDecision(
                orderId,
                received.getId(),
                Instant.now(),
                PaymentDecisionOutcome.ACCEPTED,
                received.getId()));
    sagas.recordPaymentDecision(decision);
    sagas.recordPaymentDecision(decision);
    assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.WAITING_TICKETS);
    assertThat(outbox.count()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment_schema.tbl_payment_confirmations", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void forwardsTicketSuccessAndWaitsForBookingCompletion() {
    var started =
        payments.createPayment(
            new CreatePaymentRequest(orderId, null),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    ipn.put("vnp_TxnRef", started.attemptId().toString());
    ipn.put(
        "vnp_SecureHash",
        VNPayUtil.hmacSHA512(
            "fixture-secret",
            ipn.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining("&"))));
    payments.handleIPN(ipn);
    var charge = charges.findAll().getFirst();
    sagas.recordPaymentDecision(
        new Envelope<>(
            UUID.randomUUID(),
            "booking.payment-decision.v1",
            1,
            orderId,
            "1",
            Instant.now(),
            orderId,
            null,
            null,
            new PaymentDecision(
                orderId,
                charge.getId(),
                Instant.now(),
                PaymentDecisionOutcome.ACCEPTED,
                charge.getId())));
    var result =
        new Envelope<>(
            UUID.randomUUID(),
            "ticket.issuance-result.v1",
            1,
            orderId,
            "1",
            Instant.now(),
            orderId,
            null,
            null,
            new IssuanceResult(
                orderId, charge.getId(), issuanceId, IssuanceState.COMPLETED, 1, null));
    sagas.recordIssuanceResult(result);
    assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.WAITING_TICKETS);
    var notification =
        outbox
            .findFirstByAggregateIdAndMessageTypeOrderByOccurredAtDescMessageIdDesc(
                orderId, "booking.issuance-result.v1")
            .orElseThrow();
    assertThat(
            codec
                .readEnvelope(notification.getPayload().toString(), BookingIssuanceResult.class)
                .data()
                .issuanceId())
        .isEqualTo(issuanceId);
    sagas.recordIssuanceResult(result);
    tx.executeWithoutResult(
        status -> {
          var stored = outbox.findById(notification.getMessageId()).orElseThrow();
          stored.setPublishedAt(Instant.now());
          outbox.save(stored);
        });
    jdbc.update(
        "update payment_schema.tbl_payment_sagas set next_attempt_at=? where order_id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        orderId);
    sagas.recoverSaga(orderId);
    assertThat(
            outbox.findAll().stream()
                .filter(message -> message.getMessageType().equals("ticket.issue.v1"))
                .count())
        .isEqualTo(1);
    assertThat(
            outbox.findAll().stream()
                .filter(message -> message.getMessageType().equals("booking.issuance-result.v1"))
                .count())
        .isEqualTo(2);
    var completed =
        new Envelope<>(
            UUID.randomUUID(),
            "booking.order-completed.v1",
            1,
            orderId,
            "1",
            Instant.now(),
            orderId,
            null,
            null,
            new OrderCompleted(orderId, charge.getId(), issuanceId, 1));
    sagas.recordOrderCompleted(completed);
    sagas.recordOrderCompleted(completed);
    assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.COMPLETED);
    assertThat(outbox.count()).isEqualTo(4);
  }

  @Test
  void rejectsMismatchedOrReplayedSagaResults() {
    var started =
        payments.createPayment(
            new CreatePaymentRequest(orderId, null),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    var charge =
        tx.execute(
            status -> {
              var saved =
                  charges.save(
                      com.flashticket.payment_service.entity.Charge.received(
                          attempts.findById(started.attemptId()).orElseThrow(),
                          "mismatch",
                          Instant.now(),
                          Instant.now()));
              sagas.recordSuccessfulCharge(saved);
              return saved;
            });
    UUID messageId = UUID.randomUUID();
    Instant decisionAt = Instant.now();
    sagas.recordPaymentDecision(
        new Envelope<>(
            messageId,
            "booking.payment-decision.v1",
            1,
            orderId,
            "1",
            decisionAt,
            orderId,
            null,
            null,
            new PaymentDecision(
                orderId,
                charge.getId(),
                decisionAt,
                PaymentDecisionOutcome.ACCEPTED,
                charge.getId())));
    assertThatThrownBy(
            () ->
                sagas.recordPaymentDecision(
                    new Envelope<>(
                        messageId,
                        "booking.payment-decision.v1",
                        1,
                        orderId,
                        "1",
                        decisionAt,
                        orderId,
                        null,
                        null,
                        new PaymentDecision(
                            orderId,
                            charge.getId(),
                            decisionAt,
                            PaymentDecisionOutcome.LATE,
                            null))))
        .isInstanceOf(ContractValidationException.class);
    assertThatThrownBy(
            () ->
                sagas.recordIssuanceResult(
                    new Envelope<>(
                        UUID.randomUUID(),
                        "ticket.issuance-result.v1",
                        1,
                        orderId,
                        "1",
                        Instant.now(),
                        orderId,
                        null,
                        null,
                        new IssuanceResult(
                            orderId,
                            charge.getId(),
                            issuanceId,
                            IssuanceState.COMPLETED,
                            2,
                            null))))
        .isInstanceOf(ContractValidationException.class);
    assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.WAITING_TICKETS);
    assertThat(outbox.count()).isEqualTo(2);
  }

  @Test
  void recoversSagaAfterCommitAndDeliveryFailures() throws Exception {
    var admin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
    admin.declareQueue(new org.springframework.amqp.core.Queue("payment.gov156.test.accept", true));
    admin.declareBinding(
        new Binding(
            "payment.gov156.test.accept",
            Binding.DestinationType.QUEUE,
            "payment.gov156.test.v1",
            "booking.accept-payment.v1",
            null));
    admin.purgeQueue("payment.gov156.test.accept");
    var started =
        payments.createPayment(
            new CreatePaymentRequest(orderId, null),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    tx.executeWithoutResult(
        status ->
            sagas.recordSuccessfulCharge(
                charges.save(
                    com.flashticket.payment_service.entity.Charge.received(
                        attempts.findById(started.attemptId()).orElseThrow(),
                        "recovery",
                        Instant.now(),
                        Instant.now()))));
    worker.publishOutbox();
    assertThat(outbox.findAll().getFirst().getPublishedAt()).isNotNull();
    assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.WAITING_BOOKING);
    jdbc.update(
        "update payment_schema.tbl_payment_sagas set next_attempt_at=? where order_id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        orderId);
    sagas.recoverSaga(orderId);
    assertThat(outbox.count()).isEqualTo(2);
    worker.publishOutbox();
    assertThat(rabbitTemplate.receive("payment.gov156.test.accept", 2000)).isNotNull();
    assertThat(rabbitTemplate.receive("payment.gov156.test.accept", 2000)).isNotNull();
    var firstClaim =
        tx.execute(
            status -> {
              var stored = outbox.findAll().getFirst();
              stored.setPublishedAt(null);
              stored.setNextAttemptAt(Instant.now().minusSeconds(1));
              return outbox.save(stored);
            });
    var claimed = messaging.claimOutbox(Instant.now(), Instant.now().plusSeconds(10), 1).getFirst();
    messaging.markPublished(claimed.getMessageId(), claimed.getAttempts() - 1, Instant.now());
    assertThat(outbox.findById(firstClaim.getMessageId()).orElseThrow().getPublishedAt()).isNull();
    messaging.markPublished(claimed.getMessageId(), claimed.getAttempts(), Instant.now());
    assertThat(outbox.findById(firstClaim.getMessageId()).orElseThrow().getPublishedAt())
        .isNotNull();
    UUID unroutedId = UUID.randomUUID();
    tx.executeWithoutResult(
        status ->
            messaging.enqueue(
                new Envelope<>(
                    unroutedId,
                    "ticket.issue.v1",
                    1,
                    orderId,
                    "1",
                    Instant.now(),
                    orderId,
                    null,
                    null,
                    new IssueTickets(orderId, charges.findAll().getFirst().getId(), purchase))));
    worker.publishOutbox();
    var unrouted = outbox.findById(unroutedId).orElseThrow();
    assertThat(unrouted.getPublishedAt()).isNull();
    assertThat(unrouted.getLastErrorCode()).isEqualTo("BROKER_DELIVERY_UNCONFIRMED");
    assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.WAITING_BOOKING);
  }

  @Test
  void keepsExpiryCancellationAndRefundGuards() {
    var expired = (ObjectNode) codec.toTree(purchase);
    expired
        .put("frozenAt", Instant.now().minusSeconds(60).toString())
        .put("expiresAt", Instant.now().minusSeconds(1).toString());
    purchase = codec.fromTree(expired, PurchaseSnapshot.class);
    assertThatThrownBy(
            () ->
                payments.createPayment(
                    new CreatePaymentRequest(orderId, null),
                    new MockHttpServletRequest(),
                    "buyer-1",
                    requestKey))
        .isInstanceOf(PaymentException.class);
    assertThat(attempts.count()).isZero();
    expired.put("expiresAt", Instant.now().plusSeconds(600).toString());
    purchase = codec.fromTree(expired, PurchaseSnapshot.class);
    var started =
        payments.createPayment(
            new CreatePaymentRequest(orderId, null),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    ipn.put("vnp_TxnRef", started.attemptId().toString());
    ipn.put(
        "vnp_SecureHash",
        VNPayUtil.hmacSHA512(
            "fixture-secret",
            ipn.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining("&"))));
    payments.handleIPN(ipn);
    var charge = charges.findAll().getFirst();
    var late =
        new Envelope<>(
            UUID.randomUUID(),
            "booking.payment-decision.v1",
            1,
            orderId,
            "1",
            Instant.now(),
            orderId,
            null,
            null,
            new PaymentDecision(
                orderId, charge.getId(), Instant.now(), PaymentDecisionOutcome.LATE, null));
    sagas.recordPaymentDecision(late);
    sagas.recordPaymentDecision(late);
    assertThat(refunds.count()).isEqualTo(1);
    assertThat(refunds.findAll().getFirst().getReason()).isEqualTo(RefundReason.LATE_PAYMENT);
    assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.COMPENSATING);
    assertThat(outbox.count()).isEqualTo(1);
    for (boolean cancelled : new boolean[] {false, true}) {
      setUp();
      var created =
          payments.createPayment(
              new CreatePaymentRequest(orderId, null),
              new MockHttpServletRequest(),
              "buyer-1",
              requestKey);
      ipn.put("vnp_TxnRef", created.attemptId().toString());
      ipn.put(
          "vnp_SecureHash",
          VNPayUtil.hmacSHA512(
              "fixture-secret",
              ipn.entrySet().stream()
                  .map(e -> e.getKey() + "=" + e.getValue())
                  .collect(java.util.stream.Collectors.joining("&"))));
      payments.handleIPN(ipn);
      var candidate = charges.findAll().getFirst();
      sagas.recordPaymentDecision(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.payment-decision.v1",
              1,
              orderId,
              "1",
              Instant.now(),
              orderId,
              null,
              null,
              new PaymentDecision(
                  orderId,
                  candidate.getId(),
                  Instant.now(),
                  PaymentDecisionOutcome.ACCEPTED,
                  candidate.getId())));
      if (cancelled)
        jdbc.update(
            "insert into payment_schema.tbl_event_finance_snapshots(event_id,cancellation_id,cancelled_at,received_at) values (?,?,?,?)",
            purchase.eventId(),
            UUID.randomUUID(),
            java.sql.Timestamp.from(Instant.now()),
            java.sql.Timestamp.from(Instant.now()));
      sagas.recordIssuanceResult(
          new Envelope<>(
              UUID.randomUUID(),
              "ticket.issuance-result.v1",
              1,
              orderId,
              "1",
              Instant.now(),
              orderId,
              null,
              null,
              new IssuanceResult(
                  orderId,
                  candidate.getId(),
                  issuanceId,
                  cancelled ? IssuanceState.COMPLETED : IssuanceState.FAILED,
                  cancelled ? 1 : 0,
                  cancelled ? null : "TERMINAL_FAILURE")));
      assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
          .isEqualTo(PaymentSagaState.COMPENSATING);
      assertThat(refunds.findAll()).hasSize(1);
      assertThat(refunds.findAll().getFirst().getReason())
          .isEqualTo(
              cancelled ? RefundReason.EVENT_CANCELLED : RefundReason.TICKET_ISSUANCE_FAILED);
      sagas.recordOrderCompleted(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.order-completed.v1",
              1,
              orderId,
              "1",
              Instant.now(),
              orderId,
              null,
              null,
              new OrderCompleted(orderId, candidate.getId(), issuanceId, 1)));
      assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
          .isEqualTo(PaymentSagaState.COMPENSATING);
      assertThat(refunds.count()).isEqualTo(1);
    }
  }

  @Test
  void acknowledgesMessagesOnlyAfterPaymentCommit() throws Exception {
    var started =
        payments.createPayment(
            new CreatePaymentRequest(orderId, null),
            new MockHttpServletRequest(),
            "buyer-1",
            requestKey);
    ipn.put("vnp_TxnRef", started.attemptId().toString());
    ipn.put(
        "vnp_SecureHash",
        VNPayUtil.hmacSHA512(
            "fixture-secret",
            ipn.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining("&"))));
    payments.handleIPN(ipn);
    var charge = charges.findAll().getFirst();
    var envelope =
        new Envelope<>(
            UUID.randomUUID(),
            "booking.payment-decision.v1",
            1,
            orderId,
            "1",
            Instant.now(),
            orderId,
            null,
            null,
            new PaymentDecision(
                orderId,
                charge.getId(),
                Instant.now(),
                PaymentDecisionOutcome.ACCEPTED,
                charge.getId()));
    var properties = new MessageProperties();
    properties.setDeliveryTag(17);
    var channel = mock(Channel.class);
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(sagaRepository.findById(orderId).orElseThrow().getState())
                  .isEqualTo(PaymentSagaState.WAITING_TICKETS);
              return null;
            })
        .when(channel)
        .basicAck(17, false);
    listener.onPaymentDecision(
        new Message(codec.write(envelope).getBytes(StandardCharsets.UTF_8), properties), channel);
    verify(channel).basicAck(17, false);
    reset(channel);
    var invalid =
        new Envelope<>(
            UUID.randomUUID(),
            "booking.order-completed.v1",
            1,
            orderId,
            "1",
            Instant.now(),
            orderId,
            null,
            null,
            new OrderCompleted(orderId, charge.getId(), issuanceId, 1));
    assertThatThrownBy(
            () ->
                listener.onOrderCompleted(
                    new Message(codec.write(invalid).getBytes(StandardCharsets.UTF_8), properties),
                    channel))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(channel);
  }
}
