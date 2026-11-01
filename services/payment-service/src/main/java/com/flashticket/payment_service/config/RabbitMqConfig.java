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

  @Value("${payment.exchange:flash-ticket.v1}")
  private String exchangeName;

  @Value("${payment.queues.decision:payment.booking-decision.v1}")
  private String decisionQueue;

  @Value("${payment.queues.issuance:payment.ticket-result.v1}")
  private String issuanceQueue;

  @Value("${payment.queues.completed:payment.booking-completed.v1}")
  private String completedQueue;

  @Bean
  public org.springframework.amqp.core.Declarables paymentSagaTopology() {
    var exchange = new org.springframework.amqp.core.TopicExchange(exchangeName, true, false);
    var decision = new org.springframework.amqp.core.Queue(decisionQueue, true);
    var issuance = new org.springframework.amqp.core.Queue(issuanceQueue, true);
    var completed = new org.springframework.amqp.core.Queue(completedQueue, true);
    return new org.springframework.amqp.core.Declarables(
        exchange,
        decision,
        issuance,
        completed,
        org.springframework.amqp.core.BindingBuilder.bind(decision)
            .to(exchange)
            .with("booking.payment-decision.v1"),
        org.springframework.amqp.core.BindingBuilder.bind(issuance)
            .to(exchange)
            .with("ticket.issuance-result.v1"),
        org.springframework.amqp.core.BindingBuilder.bind(completed)
            .to(exchange)
            .with("booking.order-completed.v1"));
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
