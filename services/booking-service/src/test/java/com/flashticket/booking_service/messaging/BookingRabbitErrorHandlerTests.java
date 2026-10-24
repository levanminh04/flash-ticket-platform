package com.flashticket.booking_service.messaging;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.exception.BusinessErrorCode;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorClassifier;
import com.flashticket.booking_service.observability.BookingContext;
import com.flashticket.booking_service.observability.FailureLog;
import com.flashticket.booking_service.support.LogCapture;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class BookingRabbitErrorHandlerTests {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void fatalMessageIsRejectedEvenWithXDeath(boolean previouslyDeadLettered) {
    var properties = new MessageProperties();
    properties.setConsumerQueue("booking.sales-configured.q");
    properties.setHeader("Authorization", "SENTINEL-header");
    if (previouslyDeadLettered) properties.setHeader("x-death", List.of(Map.of("count", 1L)));
    var message = new Message("SENTINEL-body".getBytes(StandardCharsets.UTF_8), properties);
    var failure =
        new ListenerExecutionFailedException(
            "SENTINEL-wrapper", new MessageConversionException("SENTINEL-conversion"), message);
    try (var logs = new LogCapture()) {
      assertThatThrownBy(() -> new BookingRabbitErrorHandler().handleError(failure))
          .isInstanceOfSatisfying(
              AmqpRejectAndDontRequeueException.class,
              error -> {
                assertThat(error.isRejectManual()).isTrue();
                assertThat(error.getCause()).isSameAs(failure);
              });
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getFormattedMessage())
          .isEqualTo("Booking operation failed.");
      assertThat(logs.events().getFirst().getKeyValuePairs())
          .anySatisfy(
              pair -> {
                assertThat(pair.key).isEqualTo("errorCode");
                assertThat(pair.value).isEqualTo("INVALID_MESSAGE");
              });
    }
  }

  @Test
  void nonfatalFailureKeepsNativeDisposition() {
    for (Throwable cause :
        List.of(
            new DataAccessResourceFailureException("SENTINEL-db"),
            new BusinessException(BusinessErrorCode.ORDER_EXPIRED))) {
      var failure =
          new ListenerExecutionFailedException("wrapper", cause, new Message(new byte[0]));
      try (var logs = new LogCapture()) {
        assertThatCode(() -> new BookingRabbitErrorHandler().handleError(failure))
            .doesNotThrowAnyException();
        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(logs.events()).hasSize(1);
        assertThat(logs.events().getFirst().getKeyValuePairs())
            .anySatisfy(
                pair -> {
                  assertThat(pair.key).isEqualTo("outcome");
                  assertThat(pair.value).isEqualTo("FAILED");
                });
      }
    }
  }

  @Test
  void nativeFatalLoggerDoesNotDumpMessage(CapturedOutput output) throws Exception {
    try (var context = LogCapture.bootJsonLogging()) {
      var failure =
          new ListenerExecutionFailedException(
              "SENTINEL-wrapper",
              new MessageConversionException("SENTINEL-conversion"),
              new Message("SENTINEL-body".getBytes(StandardCharsets.UTF_8)));
      assertThatThrownBy(() -> new BookingRabbitErrorHandler().handleError(failure))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
      assertThat(output.getOut())
          .contains("\"errorCode\":\"INVALID_MESSAGE\"")
          .doesNotContain("SENTINEL-");
    }
  }

  @Test
  void publisherNackAndReturnKeepOutboxUnsentAndLogSafeFailure() {
    for (var signal : List.of(new PublishSignal(false, false), new PublishSignal(true, true))) {
      var correlation = new CorrelationData(UUID.randomUUID().toString());
      correlation.getFuture().complete(new CorrelationData.Confirm(signal.ack(), "broker result"));
      if (signal.returned()) {
        correlation.setReturned(
            new ReturnedMessage(
                new Message("SENTINEL-payload".getBytes(), new MessageProperties()),
                312,
                "NO_ROUTE",
                "events.ex",
                "missing.key"));
      }
      var state = new OutboxFixture(UUID.randomUUID(), UUID.randomUUID());
      try (var logs = new LogCapture()) {
        recordPublishResult(state, correlation, signal.returned());
        assertThat(state.sent()).isFalse();
        assertThat(state.errorCode()).isEqualTo("MESSAGE_PUBLISH_FAILED");
        assertThat(logs.events())
            .singleElement()
            .satisfies(
                event -> {
                  assertThat(event.getFormattedMessage()).isEqualTo("Booking operation failed.");
                  assertThat(event.getMDCPropertyMap())
                      .containsEntry("correlationId", state.correlationId().toString());
                });
      }
    }
  }

  private static void recordPublishResult(
      OutboxFixture row, CorrelationData confirm, boolean returned) {
    var result = confirm.getFuture().join();
    try (var scope = BookingContext.open(Map.of("correlationId", row.correlationId().toString()))) {
      if (result.ack() && !returned && confirm.getReturned() == null) {
        row.markSent();
        return;
      }
      var failure = ErrorClassifier.of("MESSAGE_PUBLISH_FAILED", null);
      row.recordFailure(failure.code());
      FailureLog.record(
          failure, "booking.message.failed", "outbox.publish", FailureLog.Outcome.FAILED);
    }
  }

  private record PublishSignal(boolean ack, boolean returned) {}

  private static final class OutboxFixture {
    private final UUID id;
    private final UUID correlationId;
    private boolean sent;
    private String errorCode;

    private OutboxFixture(UUID id, UUID correlationId) {
      this.id = id;
      this.correlationId = correlationId;
    }

    UUID correlationId() {
      return correlationId;
    }

    boolean sent() {
      return sent;
    }

    String errorCode() {
      return errorCode;
    }

    void markSent() {
      sent = true;
    }

    void recordFailure(String code) {
      errorCode = code;
    }
  }

  @Test
  void bookingQueuesHaveMatchingDeadLetterPoliciesAndBindings() throws Exception {
    JsonNode root =
        JsonMapper.builder()
            .build()
            .readTree(Files.readString(Path.of("../../deploy/rabbitmq/definitions.json")));
    for (String queue :
        List.of(
            "booking.sales-configured.q",
            "booking.event-cancelled.q",
            "booking.accept-payment.q",
            "booking.tickets-issued.q",
            "booking.issuance-failed.q")) {
      String dlq = queue.substring(0, queue.length() - 2) + ".dlq";
      assertThat(root.get("queues"))
          .anySatisfy(node -> assertThat(node.get("name").asString()).isEqualTo(queue));
      assertThat(root.get("queues"))
          .anySatisfy(node -> assertThat(node.get("name").asString()).isEqualTo(dlq));
      assertThat(root.get("policies"))
          .anySatisfy(
              node -> {
                assertThat(Pattern.matches(node.get("pattern").asString(), queue)).isTrue();
                assertThat(node.get("definition").get("dead-letter-exchange").asString())
                    .isEqualTo("dlx.ex");
                assertThat(node.get("definition").get("dead-letter-routing-key").asString())
                    .isEqualTo(dlq);
              });
      assertThat(root.get("bindings"))
          .anySatisfy(
              node -> {
                assertThat(node.get("source").asString()).isEqualTo("dlx.ex");
                assertThat(node.get("destination").asString()).isEqualTo(dlq);
                assertThat(node.get("routing_key").asString()).isEqualTo(dlq);
              });
    }
  }
}
