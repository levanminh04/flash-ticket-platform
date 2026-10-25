package com.flashticket.payment_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.Envelope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class PaymentServiceApplicationTests {

  @Autowired ApplicationContext context;
  @Autowired JsonMapper jsonMapper;

  @Test
  void contextLoads() {}

  @Test
  void rabbitMessagesUseJsonWithTheSharedEnvelopeWireFormat() {
    assertThat(context.getBeansOfType(MessageConverter.class)).hasSize(1);
    var id = UUID.fromString("00112233-4455-4677-8899-aabbccddeeff");
    var envelope =
        new Envelope<>(
            id,
            "booking.accept-payment.v1",
            1,
            id,
            "1",
            Instant.parse("2026-10-26T00:00:00Z"),
            id,
            null,
            null,
            Map.of("amount", "120000", "currency", "VND"));
    var message =
        context.getBean(MessageConverter.class).toMessage(envelope, new MessageProperties());
    assertThat(message.getMessageProperties().getContentType())
        .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    var body = jsonMapper.readTree(message.getBody());
    assertThat(body.get("messageId").asString()).isEqualTo(id.toString());
    assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-26T00:00:00Z");
    assertThat(body.get("data").get("amount").asString()).isEqualTo("120000");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void fatalRabbitMessagesAreRejectedInManualAckModeWithoutLeakingPayload(
      boolean previouslyDeadLettered, CapturedOutput output) {
    var properties = new MessageProperties();
    properties.setHeader("Authorization", "SENTINEL-header");
    if (previouslyDeadLettered) properties.setHeader("x-death", List.of(Map.of("count", 1L)));
    var failure =
        new ListenerExecutionFailedException(
            "SENTINEL-wrapper",
            new MessageConversionException("SENTINEL-conversion"),
            new Message(
                "SENTINEL-body".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties));
    var container = rabbitContainer();
    assertThatThrownBy(() -> container.getErrorHandler().handleError(failure))
        .isInstanceOfSatisfying(
            AmqpRejectAndDontRequeueException.class,
            error -> {
              assertThat(error.isRejectManual()).isTrue();
              assertThat(error.getCause()).isSameAs(failure);
            });
    assertThat(output.getAll()).contains("Payment message failed").doesNotContain("SENTINEL-");
  }

  @Test
  void transientRabbitFailuresKeepTheNativeRetryDisposition() {
    var failure =
        new ListenerExecutionFailedException(
            "temporary failure",
            new IllegalStateException("unavailable"),
            new Message(new byte[0]));
    var container = rabbitContainer();
    assertThatCode(() -> container.getErrorHandler().handleError(failure))
        .doesNotThrowAnyException();
  }

  @SuppressWarnings("unchecked")
  private SimpleMessageListenerContainer rabbitContainer() {
    assertThat(context.containsBean("paymentRabbitContainerCustomizer")).isTrue();
    var customizer =
        (ContainerCustomizer<SimpleMessageListenerContainer>)
            context.getBean("paymentRabbitContainerCustomizer");
    var container = new SimpleMessageListenerContainer();
    customizer.configure(container);
    return container;
  }
}
