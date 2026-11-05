package com.flashticket.payment_service.config;

import com.flashticket.payment_service.messaging.PaymentRabbitErrorHandler;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class RabbitMqConfig {

  @Value("${payment.exchange:business.ex}")
  private String exchangeName;

  @Value("${payment.queues.decision:payment.decision.q}")
  private String decisionQueue;

  @Value("${payment.queues.issuance:payment.ticket-issuance-failed.q}")
  private String issuanceQueue;

  @Value("${payment.queues.completed:payment.order-completed.q}")
  private String completedQueue;

  @Value("${payment.queues.event-cancelled:payment.event-cancelled.q}")
  private String eventCancelledQueue;

  @Value("${payment.queues.finance-configured:payment.finance-configured.q}")
  private String financeConfiguredQueue;

  @Bean
  public org.springframework.amqp.core.Declarables paymentSagaTopology() {
    var exchange = new org.springframework.amqp.core.TopicExchange(exchangeName, true, false);
    // Production tắt khai báo bằng spring.rabbitmq.dynamic=false; topology dùng cho test.
    var decision =
        org.springframework.amqp.core.QueueBuilder.durable(decisionQueue).quorum().build();
    var issuance =
        org.springframework.amqp.core.QueueBuilder.durable(issuanceQueue).quorum().build();
    var completed =
        org.springframework.amqp.core.QueueBuilder.durable(completedQueue).quorum().build();
    var eventCancelled =
        org.springframework.amqp.core.QueueBuilder.durable(eventCancelledQueue).quorum().build();
    var financeConfigured =
        org.springframework.amqp.core.QueueBuilder.durable(financeConfiguredQueue).quorum().build();
    return new org.springframework.amqp.core.Declarables(
        exchange,
        decision,
        issuance,
        completed,
        eventCancelled,
        financeConfigured,
        org.springframework.amqp.core.BindingBuilder.bind(decision)
            .to(exchange)
            .with("booking.payment-decision.v1"),
        org.springframework.amqp.core.BindingBuilder.bind(issuance)
            .to(exchange)
            .with("ticket.issuance-result.v1"),
        org.springframework.amqp.core.BindingBuilder.bind(completed)
            .to(exchange)
            .with("booking.order-completed.v1"),
        org.springframework.amqp.core.BindingBuilder.bind(eventCancelled)
            .to(exchange)
            .with("event.cancelled.v1"),
        org.springframework.amqp.core.BindingBuilder.bind(financeConfigured)
            .to(exchange)
            .with("event.finance-configured.v1"));
  }

  @Bean
  MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
  }

  @Bean
  ContainerCustomizer<SimpleMessageListenerContainer> paymentRabbitContainerCustomizer() {
    return container -> container.setErrorHandler(new PaymentRabbitErrorHandler());
  }
}
