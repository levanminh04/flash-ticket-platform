package com.flashticket.payment_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.payment_service.client.BookingPurchaseClient;
import com.flashticket.payment_service.client.VNPayRefundClient;
import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.request.CreatePaymentRequest;
import com.flashticket.payment_service.entity.enums.*;
import com.flashticket.payment_service.messaging.PaymentMessageListener;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.service.*;
import com.flashticket.payment_service.support.PaymentHaInstanceConfiguration;
import com.flashticket.payment_service.support.PaymentPostgresFixture;
import com.flashticket.payment_service.worker.PaymentWorker;
import com.rabbitmq.client.Channel;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("payment-flow-test")
@Import(PaymentHaInstanceConfiguration.class)
class PaymentTwoInstanceIT {
  @Autowired ConfigurableApplicationContext first;
  ConfigurableApplicationContext second;
  @Autowired JdbcTemplate jdbc;
  @Autowired ContractCodec codec;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    PaymentPostgresFixture.registerProperties(registry, true);
  }

  @BeforeEach
  void startSecondInstance() throws Exception {
    PaymentPostgresFixture.resetDatabase();
    second = PaymentPostgresFixture.openServiceContext(true);
    assertThat(first.getBean(EntityManagerFactory.class))
        .isNotSameAs(second.getBean(EntityManagerFactory.class));
    assertThat(first.getBean("writeDs")).isNotSameAs(second.getBean("writeDs"));
    assertThat(first.getBean("readDs")).isNotSameAs(second.getBean("readDs"));
  }

  @AfterEach
  void stopSecondInstance() {
    first.getBean(RabbitListenerEndpointRegistry.class).stop();
    if (second != null) {
      second.close();
      second = null;
    }
  }

  @Test
  void twoContextsSerializePaymentStartsAndDuplicateIpn() throws Exception {
    var primaryRead =
        new org.springframework.transaction.support.TransactionTemplate(
            first.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    var purchase = PaymentPostgresFixture.purchase(first, UUID.randomUUID(), "buyer-1");
    for (var context : List.of(first, second))
      when(context
              .getBean(BookingPurchaseClient.class)
              .preparePurchaseSnapshot(purchase.orderId(), "buyer-1"))
          .thenReturn(purchase);
    UUID key = UUID.randomUUID();
    var ready = new CountDownLatch(2);
    var go = new CountDownLatch(1);
    com.flashticket.payment_service.dto.response.PaymentStart started;
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return first
                    .getBean(PaymentService.class)
                    .createPayment(
                        new CreatePaymentRequest(purchase.orderId(), null),
                        new MockHttpServletRequest(),
                        "buyer-1",
                        key);
              });
      var b =
          pool.submit(
              () -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return second
                    .getBean(PaymentService.class)
                    .createPayment(
                        new CreatePaymentRequest(purchase.orderId(), null),
                        new MockHttpServletRequest(),
                        "buyer-1",
                        key);
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      started = a.get(20, TimeUnit.SECONDS);
      assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(started);
      var ipn = PaymentPostgresFixture.signedIpn(started);
      var ipnReady = new CountDownLatch(2);
      var ipnGo = new CountDownLatch(1);
      var c =
          pool.submit(
              () -> {
                ipnReady.countDown();
                assertThat(ipnGo.await(10, TimeUnit.SECONDS)).isTrue();
                return first.getBean(PaymentService.class).handleIPN(ipn);
              });
      var d =
          pool.submit(
              () -> {
                ipnReady.countDown();
                assertThat(ipnGo.await(10, TimeUnit.SECONDS)).isTrue();
                return second.getBean(PaymentService.class).handleIPN(ipn);
              });
      assertThat(ipnReady.await(10, TimeUnit.SECONDS)).isTrue();
      ipnGo.countDown();
      assertThat(c.get(20, TimeUnit.SECONDS) + d.get(20, TimeUnit.SECONDS))
          .contains("\"RspCode\":\"00\"");
    } finally {
      go.countDown();
    }
    assertThat(
            primaryRead.<Long>execute(
                status -> first.getBean(PaymentAttemptRepository.class).count()))
        .isEqualTo(1);
    assertThat(primaryRead.<Long>execute(status -> first.getBean(ChargeRepository.class).count()))
        .isEqualTo(1);
    assertThat(
            primaryRead.<Long>execute(status -> first.getBean(PaymentSagaRepository.class).count()))
        .isEqualTo(1);
    assertThat(
            primaryRead.<Long>execute(
                status -> first.getBean(OutboxMessageRepository.class).count()))
        .isEqualTo(1);
    assertThat(primaryRead.<Long>execute(status -> first.getBean(RefundRepository.class).count()))
        .isZero();
  }

  @Test
  void twoWorkersFenceClaimsAndAllowStableIdRedelivery() throws Exception {
    var primaryRead =
        new org.springframework.transaction.support.TransactionTemplate(
            first.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    var purchase = PaymentPostgresFixture.purchase(first, UUID.randomUUID(), "buyer-1");
    when(first
            .getBean(BookingPurchaseClient.class)
            .preparePurchaseSnapshot(purchase.orderId(), "buyer-1"))
        .thenReturn(purchase);
    var start =
        first
            .getBean(PaymentService.class)
            .createPayment(
                new CreatePaymentRequest(purchase.orderId(), null),
                new MockHttpServletRequest(),
                "buyer-1",
                UUID.randomUUID());
    first.getBean(PaymentService.class).handleIPN(PaymentPostgresFixture.signedIpn(start));
    var rabbit = first.getBean(RabbitTemplate.class);
    var admin = new RabbitAdmin(rabbit.getConnectionFactory());
    admin.declareQueue(new Queue("payment.ha.capture", true));
    for (String routing :
        List.of(
            "booking.accept-payment.v1",
            "ticket.issue.v1",
            "ticket.issued.v1",
            "booking.issuance-failed.v1"))
      admin.declareBinding(
          new Binding(
              "payment.ha.capture",
              Binding.DestinationType.QUEUE,
              "payment.gov156.test.v1",
              routing,
              null));
    admin.purgeQueue("payment.ha.capture");
    try {
      Instant now = Instant.now();
      var ready = new CountDownLatch(2);
      var go = new CountDownLatch(1);
      var claims = new java.util.ArrayList<com.flashticket.payment_service.entity.OutboxMessage>();
      try (var pool = Executors.newFixedThreadPool(2)) {
        var a =
            pool.submit(
                () -> {
                  ready.countDown();
                  assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                  return first
                      .getBean(MessagingService.class)
                      .claimOutbox(now, now.plusSeconds(10), 1);
                });
        var b =
            pool.submit(
                () -> {
                  ready.countDown();
                  assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                  return second
                      .getBean(MessagingService.class)
                      .claimOutbox(now, now.plusSeconds(10), 1);
                });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        claims.addAll(a.get(20, TimeUnit.SECONDS));
        claims.addAll(b.get(20, TimeUnit.SECONDS));
      } finally {
        go.countDown();
      }
      assertThat(claims).hasSize(1);
      var claimed = claims.getFirst();
      var properties = new MessageProperties();
      properties.setMessageId(claimed.getMessageId().toString());
      properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
      var correlation = new CorrelationData(claimed.getMessageId().toString());
      // Broker đã ACK nhưng cố ý chưa mark DB: mô hình crash window hợp lệ.
      rabbit.send(
          "payment.gov156.test.v1",
          claimed.getMessageType(),
          new Message(claimed.getPayload().toString().getBytes(StandardCharsets.UTF_8), properties),
          correlation);
      assertThat(correlation.getFuture().get(10, TimeUnit.SECONDS).isAck()).isTrue();
      assertThat(correlation.getReturned()).isNull();
      var reclaimer = second.getBean(MessagingService.class);
      var reclaimed = reclaimer.claimOutbox(now.plusSeconds(11), now.plusSeconds(20), 1).getFirst();
      assertThat(reclaimed.getMessageId()).isEqualTo(claimed.getMessageId());
      assertThat(reclaimed.getAttempts()).isEqualTo(claimed.getAttempts() + 1);
      first
          .getBean(MessagingService.class)
          .markPublished(claimed.getMessageId(), claimed.getAttempts(), Instant.now());
      first
          .getBean(MessagingService.class)
          .rescheduleOutbox(claimed.getMessageId(), claimed.getAttempts(), now, "STALE");
      var stored =
          primaryRead
              .execute(
                  status ->
                      first.getBean(OutboxMessageRepository.class).findById(claimed.getMessageId()))
              .orElseThrow();
      assertThat(stored.getPublishedAt()).isNull();
      assertThat(stored.getLastErrorCode()).isNull();
      assertThat(stored.getAttempts()).isEqualTo(reclaimed.getAttempts());
      jdbc.update(
          "update payment_schema.tbl_outbox_messages set next_attempt_at=now()-interval '1 second' where message_id=?",
          claimed.getMessageId());
      var publishGo = new CountDownLatch(1);
      try (var pool = Executors.newFixedThreadPool(2)) {
        var a =
            pool.submit(
                () -> {
                  assertThat(publishGo.await(10, TimeUnit.SECONDS)).isTrue();
                  first.getBean(PaymentWorker.class).publishOutbox();
                  return null;
                });
        var b =
            pool.submit(
                () -> {
                  assertThat(publishGo.await(10, TimeUnit.SECONDS)).isTrue();
                  second.getBean(PaymentWorker.class).publishOutbox();
                  return null;
                });
        publishGo.countDown();
        a.get(20, TimeUnit.SECONDS);
        b.get(20, TimeUnit.SECONDS);
      } finally {
        publishGo.countDown();
      }
      assertThat(
              primaryRead
                  .execute(
                      status ->
                          first
                              .getBean(OutboxMessageRepository.class)
                              .findById(claimed.getMessageId()))
                  .orElseThrow()
                  .getPublishedAt())
          .isNotNull();
      var receiptA = rabbit.receive("payment.ha.capture", 2000);
      var receiptB = rabbit.receive("payment.ha.capture", 2000);
      assertThat(receiptA).isNotNull();
      assertThat(receiptB).isNotNull();
      assertThat(receiptA.getMessageProperties().getMessageId())
          .isEqualTo(claimed.getMessageId().toString());
      assertThat(receiptB.getMessageProperties().getMessageId())
          .isEqualTo(claimed.getMessageId().toString());
      assertThat(rabbit.receive("payment.ha.capture", 100)).isNull();
    } finally {
      admin.deleteQueue("payment.ha.capture");
    }
  }

  @Test
  void twoContextsRecoverTheSameSagaAndRefundRunOnce() throws Exception {
    var primaryRead =
        new org.springframework.transaction.support.TransactionTemplate(
            first.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    UUID eventId = UUID.randomUUID();
    var purchase = PaymentPostgresFixture.purchase(first, eventId, "buyer-1");
    when(first
            .getBean(BookingPurchaseClient.class)
            .preparePurchaseSnapshot(purchase.orderId(), "buyer-1"))
        .thenReturn(purchase);
    var start =
        first
            .getBean(PaymentService.class)
            .createPayment(
                new CreatePaymentRequest(purchase.orderId(), null),
                new MockHttpServletRequest(),
                "buyer-1",
                UUID.randomUUID());
    first.getBean(PaymentService.class).handleIPN(PaymentPostgresFixture.signedIpn(start));
    jdbc.update("update payment_schema.tbl_outbox_messages set published_at=now()");
    jdbc.update(
        "update payment_schema.tbl_payment_sagas set next_attempt_at=now()-interval '1 second'");
    var go = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                first.getBean(PaymentSagaService.class).recoverSaga(purchase.orderId());
                return null;
              });
      var b =
          pool.submit(
              () -> {
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                second.getBean(PaymentSagaService.class).recoverSaga(purchase.orderId());
                return null;
              });
      go.countDown();
      a.get(20, TimeUnit.SECONDS);
      b.get(20, TimeUnit.SECONDS);
    } finally {
      go.countDown();
    }
    assertThat(
            primaryRead.<Long>execute(
                status -> first.getBean(OutboxMessageRepository.class).count()))
        .isEqualTo(2);
    UUID refundEvent = UUID.randomUUID();
    PaymentPostgresFixture.seedConfirmedOrder(first, refundEvent, "organizer-1");
    PaymentPostgresFixture.seedConfirmedOrder(first, refundEvent, "organizer-1");
    var json = first.getBean(tools.jackson.databind.json.JsonMapper.class);
    var node =
        (ObjectNode)
            json.readTree(
                new ClassPathResource("contracts/EventCancellation.json")
                    .getContentAsString(StandardCharsets.UTF_8));
    node.put("eventId", refundEvent.toString()).put("cancellationId", UUID.randomUUID().toString());
    ((ObjectNode) node.get("sales"))
        .put("eventId", refundEvent.toString())
        .put("eventStatus", "CANCELLED");
    ((ObjectNode) node.get("access"))
        .put("eventId", refundEvent.toString())
        .put("eventStatus", "CANCELLED");
    var cancellation =
        new Envelope<>(
            UUID.randomUUID(),
            "event.cancelled.v1",
            1,
            refundEvent,
            "1",
            Instant.now(),
            UUID.randomUUID(),
            null,
            null,
            codec.fromTree(node, EventCancellation.class));
    first.getBean(RefundService.class).recordEventCancellation(cancellation);
    second.getBean(RefundService.class).recordEventCancellation(cancellation);
    var run =
        primaryRead
            .execute(status -> first.getBean(EventRefundRunRepository.class).findAll())
            .getFirst();
    var selectGo = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                assertThat(selectGo.await(10, TimeUnit.SECONDS)).isTrue();
                first.getBean(RefundService.class).selectEventRefundOrders(run.getId(), 2);
                return null;
              });
      var b =
          pool.submit(
              () -> {
                assertThat(selectGo.await(10, TimeUnit.SECONDS)).isTrue();
                second.getBean(RefundService.class).selectEventRefundOrders(run.getId(), 2);
                return null;
              });
      selectGo.countDown();
      a.get(20, TimeUnit.SECONDS);
      b.get(20, TimeUnit.SECONDS);
    } finally {
      selectGo.countDown();
    }
    var selected =
        primaryRead
            .execute(status -> first.getBean(EventRefundRunRepository.class).findById(run.getId()))
            .orElseThrow();
    assertThat(selected.getSelectedOrderCount()).isEqualTo(2);
    assertThat(selected.getSelectionComplete()).isTrue();
    assertThat(
            primaryRead.<Long>execute(
                status -> first.getBean(EventRefundItemRepository.class).count()))
        .isEqualTo(2);
    assertThat(primaryRead.<Long>execute(status -> first.getBean(RefundRepository.class).count()))
        .isEqualTo(2);
    var refundId =
        primaryRead
            .execute(status -> first.getBean(RefundRepository.class).findAll())
            .getFirst()
            .getId();
    var sendGo = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                assertThat(sendGo.await(10, TimeUnit.SECONDS)).isTrue();
                first.getBean(RefundService.class).sendRefund(refundId);
                return null;
              });
      var b =
          pool.submit(
              () -> {
                assertThat(sendGo.await(10, TimeUnit.SECONDS)).isTrue();
                second.getBean(RefundService.class).pollRefund(refundId);
                return null;
              });
      sendGo.countDown();
      a.get(20, TimeUnit.SECONDS);
      b.get(20, TimeUnit.SECONDS);
    } finally {
      sendGo.countDown();
    }
    assertThat(
            primaryRead.<Long>execute(
                status -> first.getBean(RefundAttemptRepository.class).count()))
        .isEqualTo(1);
    assertThat(
            primaryRead
                .execute(status -> first.getBean(RefundRepository.class).findById(refundId))
                .orElseThrow()
                .getState())
        .isEqualTo(RefundState.SUCCEEDED);
    for (var refund :
        primaryRead.execute(status -> first.getBean(RefundRepository.class).findAll()))
      first.getBean(RefundService.class).sendRefund(refund.getId());
    assertThat(
            primaryRead
                .execute(
                    status -> first.getBean(EventRefundRunRepository.class).findById(run.getId()))
                .orElseThrow()
                .getCompletedAt())
        .isNotNull();
    verifyNoInteractions(
        first.getBean(VNPayRefundClient.class), second.getBean(VNPayRefundClient.class));
  }

  @Test
  void redeliveredConsumerMessagesCommitBeforeManualAck() throws Exception {
    var primaryRead =
        new org.springframework.transaction.support.TransactionTemplate(
            first.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    var purchase = PaymentPostgresFixture.purchase(first, UUID.randomUUID(), "buyer-1");
    when(first
            .getBean(BookingPurchaseClient.class)
            .preparePurchaseSnapshot(purchase.orderId(), "buyer-1"))
        .thenReturn(purchase);
    var start =
        first
            .getBean(PaymentService.class)
            .createPayment(
                new CreatePaymentRequest(purchase.orderId(), null),
                new MockHttpServletRequest(),
                "buyer-1",
                UUID.randomUUID());
    first.getBean(PaymentService.class).handleIPN(PaymentPostgresFixture.signedIpn(start));
    var charge =
        primaryRead.execute(status -> first.getBean(ChargeRepository.class).findAll()).getFirst();
    var decision =
        new Envelope<>(
            UUID.randomUUID(),
            "booking.payment-decision.v1",
            1,
            purchase.orderId(),
            "1",
            Instant.now(),
            purchase.orderId(),
            null,
            null,
            new PaymentDecision(
                purchase.orderId(),
                charge.getId(),
                Instant.now(),
                PaymentDecisionOutcome.ACCEPTED,
                charge.getId()));
    var channel = mock(Channel.class);
    var properties = new MessageProperties();
    properties.setDeliveryTag(17);
    var message = new Message(codec.write(decision).getBytes(StandardCharsets.UTF_8), properties);
    jdbc.execute(
        "alter table payment_schema.tbl_payment_confirmations add constraint ha_reject_confirmation check(false) not valid");
    try {
      assertThatThrownBy(
              () ->
                  second.getBean(PaymentMessageListener.class).onPaymentDecision(message, channel))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      verify(channel).basicNack(17, false, true);
      verify(channel, never()).basicAck(anyLong(), anyBoolean());
      assertThat(
              primaryRead.<Long>execute(
                  status -> first.getBean(PaymentConfirmationRepository.class).count()))
          .isZero();
    } finally {
      jdbc.execute(
          "alter table payment_schema.tbl_payment_confirmations drop constraint ha_reject_confirmation");
    }
    reset(channel);
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(
                      primaryRead.<Long>execute(
                          status -> first.getBean(PaymentConfirmationRepository.class).count()))
                  .isEqualTo(1);
              return null;
            })
        .when(channel)
        .basicAck(17, false);
    second.getBean(PaymentMessageListener.class).onPaymentDecision(message, channel);
    verify(channel).basicAck(17, false);
    UUID issuanceId = UUID.randomUUID();
    var issuance =
        new Envelope<>(
            UUID.randomUUID(),
            "ticket.issuance-result.v1",
            1,
            purchase.orderId(),
            "1",
            Instant.now(),
            purchase.orderId(),
            null,
            null,
            new IssuanceResult(
                purchase.orderId(), charge.getId(), issuanceId, IssuanceState.COMPLETED, 1, null));
    var completed =
        new Envelope<>(
            UUID.randomUUID(),
            "booking.order-completed.v1",
            1,
            purchase.orderId(),
            "1",
            Instant.now(),
            purchase.orderId(),
            null,
            null,
            new OrderCompleted(purchase.orderId(), charge.getId(), issuanceId, 1));
    var finance =
        new Envelope<>(
            UUID.randomUUID(),
            "event.finance-configured.v1",
            1,
            purchase.eventId(),
            "1",
            Instant.now(),
            purchase.orderId(),
            null,
            null,
            new FinanceConfiguration(
                purchase.eventId(),
                "organizer-1",
                Instant.now().minusSeconds(3600),
                "0.075",
                "1",
                "VND"));
    var json = first.getBean(tools.jackson.databind.json.JsonMapper.class);
    var node =
        (ObjectNode)
            json.readTree(
                new ClassPathResource("contracts/EventCancellation.json")
                    .getContentAsString(StandardCharsets.UTF_8));
    node.put("eventId", purchase.eventId().toString())
        .put("cancellationId", UUID.randomUUID().toString());
    ((ObjectNode) node.get("sales"))
        .put("eventId", purchase.eventId().toString())
        .put("eventStatus", "CANCELLED");
    ((ObjectNode) node.get("access"))
        .put("eventId", purchase.eventId().toString())
        .put("eventStatus", "CANCELLED");
    var cancelled =
        new Envelope<>(
            UUID.randomUUID(),
            "event.cancelled.v1",
            1,
            purchase.eventId(),
            "1",
            Instant.now(),
            purchase.orderId(),
            null,
            null,
            codec.fromTree(node, EventCancellation.class));
    first.getBean(RabbitListenerEndpointRegistry.class).start();
    second.getBean(RabbitListenerEndpointRegistry.class).start();
    var rabbit = first.getBean(RabbitTemplate.class);
    // Gửi lặp cùng ID/hash qua queue thật; mỗi stage đợi receipt đã commit.
    for (Envelope<?> envelope : List.of(decision, issuance, completed, finance, cancelled)) {
      var delivery = new MessageProperties();
      delivery.setContentType(MessageProperties.CONTENT_TYPE_JSON);
      delivery.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      delivery.setMessageId(envelope.messageId().toString());
      for (int i = 0; i < 2; i++) {
        // Giống outbox producer: wire codec + bytes, không thêm __TypeId__ từ object test.
        rabbit.send(
            "payment.gov156.test.v1",
            envelope.messageType(),
            new Message(codec.write(envelope).getBytes(StandardCharsets.UTF_8), delivery));
      }
      Awaitility.await()
          .alias("Receipt committed for " + envelope.messageType())
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(
              () ->
                  assertThat(
                          jdbc.queryForObject(
                              "select count(*) from payment_schema.tbl_inbox_messages where message_id=?",
                              Long.class,
                              envelope.messageId()))
                      .isEqualTo(1));
    }
    assertThat(
            primaryRead.<Long>execute(
                status -> first.getBean(PaymentConfirmationRepository.class).count()))
        .isEqualTo(1);
    assertThat(
            primaryRead.<Long>execute(
                status -> first.getBean(EventRefundRunRepository.class).count()))
        .isEqualTo(1);
    assertThat(
            primaryRead
                .execute(
                    status ->
                        first.getBean(PaymentSagaRepository.class).findById(purchase.orderId()))
                .orElseThrow()
                .getState())
        .isEqualTo(PaymentSagaState.COMPLETED);
    second.getBean(RabbitListenerEndpointRegistry.class).stop();
    second.close();
    second = PaymentPostgresFixture.openServiceContext(true);
    var secondPrimaryRead =
        new org.springframework.transaction.support.TransactionTemplate(
            second.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    assertThat(
            secondPrimaryRead.<Long>execute(
                status -> second.getBean(EventRefundRunRepository.class).count()))
        .isEqualTo(1);
    second.getBean(PaymentWorker.class).selectEventRefundOrders();
    second.getBean(PaymentWorker.class).sendRefunds();
    assertThat(
            secondPrimaryRead.<Long>execute(
                status -> second.getBean(RefundRepository.class).count()))
        .isEqualTo(1);
    assertThat(
            secondPrimaryRead
                .execute(status -> second.getBean(RefundRepository.class).findAll())
                .getFirst()
                .getState())
        .isEqualTo(RefundState.SUCCEEDED);
  }
}
