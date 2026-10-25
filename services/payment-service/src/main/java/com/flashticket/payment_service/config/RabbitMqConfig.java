package com.flashticket.payment_service.config;

import com.flashticket.payment_service.messaging.PaymentRabbitErrorHandler;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class RabbitMqConfig {

  @Bean
  MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
  }

  @Bean
  ContainerCustomizer<SimpleMessageListenerContainer> paymentRabbitContainerCustomizer() {
    return container -> container.setErrorHandler(new PaymentRabbitErrorHandler());
  }
}
