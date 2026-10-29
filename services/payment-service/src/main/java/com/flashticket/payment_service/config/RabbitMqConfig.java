package com.flashticket.payment_service.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class RabbitMqConfig {
  /** Dùng mapper chung; topology và consumer được triển khai ở task riêng. */
  @Bean
  MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(
        jsonMapper,
        "com.flashticket.payment_service.dto.message",
        "com.flashticket.payment_service.dto.snapshot",
        "com.flashticket.payment_service.dto.response");
  }
}
