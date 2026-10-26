package com.flashticket.user_service.messaging;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.exception.BusinessException;
import com.flashticket.user_service.exception.ErrorCode;
import com.flashticket.user_service.support.LogCapture;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(OutputCaptureExtension.class)
class UserRabbitErrorHandlerTests {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void fatalMessageIsRejectedEvenWithXDeath(boolean previouslyDeadLettered) {
    var properties = new MessageProperties();
    properties.setConsumerQueue("user.identity.q");
    properties.setHeader("Authorization", "SENTINEL-header");
    if (previouslyDeadLettered) properties.setHeader("x-death", List.of(Map.of("count", 1L)));
    var message = new Message("SENTINEL-body".getBytes(StandardCharsets.UTF_8), properties);
    var failure =
        new ListenerExecutionFailedException(
            "SENTINEL-wrapper", new MessageConversionException("SENTINEL-conversion"), message);
    try (var logs = new LogCapture()) {
      assertThatThrownBy(() -> new UserRabbitErrorHandler().handleError(failure))
          .isInstanceOfSatisfying(
              AmqpRejectAndDontRequeueException.class,
              error -> {
                assertThat(error.isRejectManual()).isTrue();
                assertThat(error.getCause()).isSameAs(failure);
              });
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getFormattedMessage())
          .contains("RabbitMQ message processing failed", ErrorCode.INVALID_MESSAGE.message());
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
            new BusinessException(ErrorCode.DATA_CONFLICT))) {
      var failure =
          new ListenerExecutionFailedException("wrapper", cause, new Message(new byte[0]));
      try (var logs = new LogCapture()) {
        assertThatCode(() -> new UserRabbitErrorHandler().handleError(failure))
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
      assertThatThrownBy(() -> new UserRabbitErrorHandler().handleError(failure))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
      assertThat(output.getOut())
          .contains("\"errorCode\":\"INVALID_MESSAGE\"")
          .doesNotContain("SENTINEL-");
    }
  }
}
