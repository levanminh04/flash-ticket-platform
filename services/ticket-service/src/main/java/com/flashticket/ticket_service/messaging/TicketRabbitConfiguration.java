package com.flashticket.ticket_service.messaging;

import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class TicketRabbitConfiguration {
  @Bean
  MessageConverter ticketRabbitMessageConverter(JsonMapper json) {
    // Resolve concrete @RabbitListener argument types, not producer-specific Java DTO headers.
    return new JacksonJsonMessageConverter(
        json, "com.flashticket.ticket_service", "com.flashticket.ticket_service.*");
  }

  @Bean
  ContainerCustomizer<SimpleMessageListenerContainer> ticketRabbitContainerCustomizer() {
    return container -> container.setErrorHandler(new TicketRabbitErrorHandler());
  }
}
