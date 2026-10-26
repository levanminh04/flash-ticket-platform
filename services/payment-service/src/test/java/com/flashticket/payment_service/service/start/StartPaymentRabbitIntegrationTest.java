package com.flashticket.payment_service.service.start;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.payment_service.dev.booking.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import com.flashticket.payment_service.gateway.*;
import com.flashticket.payment_service.hashing.ContractHashService;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.service.payment.PaymentQueryService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${PAYMENT_TEST_DB_URL:jdbc:postgresql://127.0.0.1:5443/payment_db?currentSchema=pg_catalog}",
      "spring.datasource.username=payment_app",
      "spring.datasource.password=simulator-local-only",
      "spring.autoconfigure.exclude=",
      "flashticket.payment.vnpay.enabled=true",
      "flashticket.payment.vnpay.tmn-code=TEST1234",
      "flashticket.payment.vnpay.hash-secret=key",
      "flashticket.payment.vnpay.return-url=https://merchant.example.test/api/v1/payments/vnpay/return",
      "flashticket.payment.vnpay.ipn-url=https://merchant.example.test/api/v1/payments/vnpay/ipn",
      "flashticket.payment.vnpay.sandbox-payer-ip=127.0.0.1",
      "flashticket.payment.vnpay.api-client-ip=127.0.0.1"
    })
@ActiveProfiles("payment-simulator")
@AutoConfigureMockMvc
@Import(StartPaymentRabbitIntegrationTest.TimeConfiguration.class)
class StartPaymentRabbitIntegrationTest {
  @Autowired StartPaymentProcessor processor;
  @Autowired ContractHashService hashes;
  @Autowired ContractCodec codec;
  @Autowired RabbitTemplate rabbit;
  @Autowired PaymentQueryService queries;
  @Autowired PaymentAttemptRepository attempts;
  @Autowired OrderPaymentSnapshotRepository snapshots;
  @Autowired InboxMessageRepository inbox;
  @Autowired MutableClock clock;
  @Autowired MockMvc mvc;
  @MockitoSpyBean PaymentGateway gateway;

  @BeforeEach
  void resetTimeAndGateway() {
    clock.now.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    reset(gateway);
  }

  Envelope<StartPayment> command() {
    return new BookingCommandFactory(hashes, clock)
        .create("simulator-buyer", "100000", Duration.ofMinutes(15));
  }

  @Test
  void rabbitDeliveryProducesPersistedUrlAndReplayReturnsSameAttemptAfterExpiry() throws Exception {
    var command = command();
    BookingSimulator.publish(codec.write(command), command.messageId().toString());
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        inbox.findById(
                            new InboxMessageId("payment.start-requested.v1", command.messageId())))
                    .isPresent());
    var view = queries.getPayment(command.aggregateId(), command.messageId(), "simulator-buyer");
    assertThat(view.latestAttempt().paymentUrl())
        .startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")
        .contains("vnp_Amount=10000000", "vnp_SecureHash=", "vnp_TmnCode=TEST1234");
    assertThat(view.startRequest().state().name()).isEqualTo("CREATED");
    mvc.perform(
            get("/api/v1/orders/{id}/payment", command.aggregateId())
                .param("requestId", command.messageId().toString())
                .with(
                    jwt()
                        .jwt(j -> j.subject("simulator-buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.latestAttempt.attemptId")
                .value(view.latestAttempt().attemptId().toString()));
    clock.now.updateAndGet(now -> now.plusSeconds(901));
    var replay = processor.handle(codec.write(command));
    assertThat(replay.attemptId()).isEqualTo(view.latestAttempt().attemptId());
    assertThat(
            queries
                .getPayment(command.aggregateId(), command.messageId(), "simulator-buyer")
                .latestAttempt()
                .paymentUrl())
        .isEqualTo(view.latestAttempt().paymentUrl());
    assertThat(
            attempts.findAll().stream().filter(a -> a.getOrderId().equals(command.aggregateId())))
        .hasSize(1);
  }

  @Test
  void concurrentNewRequestsCreateOneAttemptAndDurablyRejectTheOther() throws Exception {
    var first = command();
    var second = newRequest(first);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                gate.await();
                return processor.handle(codec.write(first));
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return processor.handle(codec.write(second));
              });
      gate.countDown();
      var results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
      assertThat(results)
          .extracting(StartPaymentResult::state)
          .containsExactlyInAnyOrder(
              StartPaymentResultState.CREATED, StartPaymentResultState.REJECTED);
      assertThat(
              results.stream()
                  .filter(r -> r.state() == StartPaymentResultState.REJECTED)
                  .findFirst()
                  .orElseThrow()
                  .failureCode())
          .isEqualTo("PAYMENT_IN_PROGRESS");
    }
    assertThat(attempts.findAll().stream().filter(a -> a.getOrderId().equals(first.aggregateId())))
        .hasSize(1);
  }

  @Test
  void expiredCommandStillCreatesOwnershipSnapshotAndPollableRejection() {
    var command = command();
    clock.now.updateAndGet(now -> now.plusSeconds(901));
    var result = processor.handle(codec.write(command));
    assertThat(result.failureCode()).isEqualTo("ORDER_EXPIRED");
    var view = queries.getPayment(command.aggregateId(), command.messageId(), "simulator-buyer");
    assertThat(view.latestAttempt()).isNull();
    assertThat(view.startRequest().failureCode()).isEqualTo("ORDER_EXPIRED");
  }

  @Test
  void failedUrlGenerationRollsBackSnapshotAttemptAndInboxAndCanRetry() {
    var command = command();
    doThrow(new GatewayException(GatewayException.Code.UNAVAILABLE))
        .when(gateway)
        .createPayment(any());
    assertThatThrownBy(() -> processor.handle(codec.write(command)))
        .isInstanceOf(GatewayException.class);
    assertThat(snapshots.findById(command.aggregateId())).isEmpty();
    assertThat(
            inbox.findById(new InboxMessageId("payment.start-requested.v1", command.messageId())))
        .isEmpty();
    assertThat(
            attempts.findAll().stream().filter(a -> a.getOrderId().equals(command.aggregateId())))
        .isEmpty();
    reset(gateway);
    assertThat(processor.handle(codec.write(command)).state())
        .isEqualTo(StartPaymentResultState.CREATED);
  }

  @Test
  void changedContentWithSameMessageIdCannotOverwriteOriginalOutcome() {
    var original = command();
    var result = processor.handle(codec.write(original));
    var changed =
        new Envelope<>(
            original.messageId(),
            original.messageType(),
            1,
            original.aggregateId(),
            "1",
            original.occurredAt().plusSeconds(1),
            original.correlationId(),
            null,
            null,
            original.data());
    assertThatThrownBy(() -> processor.handle(codec.write(changed)))
        .isInstanceOf(ContractValidationException.class);
    assertThat(processor.handle(codec.write(original))).isEqualTo(result);
  }

  @Test
  void malformedMessageGoesToDlqWithoutCreatingAnAttempt() {
    rabbit.send(
        BookingSimulator.EXCHANGE,
        BookingSimulator.ROUTING_KEY,
        new Message(
            "{\"invalid\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            new MessageProperties()));
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              var message = rabbit.receive(BookingSimulator.DLQ);
              assertThat(message).isNotNull();
              assertThat(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                  .isEqualTo("{\"invalid\":true}");
            });
  }

  @Test
  void publisherRejectsUnroutableCommand() {
    var command = command();
    rabbit.execute(
        channel -> {
          channel.queueUnbind(
              BookingSimulator.QUEUE, BookingSimulator.EXCHANGE, BookingSimulator.ROUTING_KEY);
          return null;
        });
    try {
      assertThatThrownBy(
              () -> BookingSimulator.publish(codec.write(command), command.messageId().toString()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Unroutable");
    } finally {
      rabbit.execute(
          channel -> {
            channel.queueBind(
                BookingSimulator.QUEUE, BookingSimulator.EXCHANGE, BookingSimulator.ROUTING_KEY);
            return null;
          });
    }
  }

  @Test
  void brokerRetriesTransientFailureWithoutLeavingPartialAttempt() throws Exception {
    var command = command();
    doThrow(new GatewayException(GatewayException.Code.UNAVAILABLE))
        .doCallRealMethod()
        .when(gateway)
        .createPayment(any());
    BookingSimulator.publish(codec.write(command), command.messageId().toString());
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        inbox.findById(
                            new InboxMessageId("payment.start-requested.v1", command.messageId())))
                    .isPresent());
    assertThat(
            attempts.findAll().stream().filter(a -> a.getOrderId().equals(command.aggregateId())))
        .hasSize(1);
    assertThat(
            queries
                .getPayment(command.aggregateId(), command.messageId(), "simulator-buyer")
                .latestAttempt()
                .paymentUrl())
        .contains("vnp_SecureHash=");
  }

  @Test
  void transactionCompletionFailureIsRetriedBeforeAcknowledgment() throws Exception {
    var command = command();
    var failFirstCommit = new java.util.concurrent.atomic.AtomicBoolean(true);
    doAnswer(
            invocation -> {
              var url = invocation.callRealMethod();
              if (failFirstCommit.getAndSet(false)) {
                org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                          @Override
                          public void beforeCommit(boolean readOnly) {
                            throw new IllegalStateException(
                                "Injected transaction completion failure");
                          }
                        });
              }
              return url;
            })
        .when(gateway)
        .createPayment(any());
    BookingSimulator.publish(codec.write(command), command.messageId().toString());
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        inbox.findById(
                            new InboxMessageId("payment.start-requested.v1", command.messageId())))
                    .isPresent());
    assertThat(failFirstCommit).isFalse();
    assertThat(
            attempts.findAll().stream().filter(a -> a.getOrderId().equals(command.aggregateId())))
        .hasSize(1);
    assertThat(
            queries
                .getPayment(command.aggregateId(), command.messageId(), "simulator-buyer")
                .startRequest()
                .state()
                .name())
        .isEqualTo("CREATED");
  }

  private Envelope<StartPayment> newRequest(Envelope<StartPayment> original) {
    var id = UUID.randomUUID();
    var data =
        new StartPayment(
            id,
            UUID.randomUUID(),
            original.data().requestHash(),
            original.data().provider(),
            original.data().purchase());
    return new Envelope<>(
        id,
        original.messageType(),
        1,
        original.aggregateId(),
        "1",
        original.occurredAt(),
        original.correlationId(),
        null,
        null,
        data);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TimeConfiguration {
    @Bean
    @Primary
    MutableClock simulatorTestClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }
}
