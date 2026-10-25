package com.flashticket.booking_service.config;

import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Cấu hình chuyển đổi JSON và xử lý lỗi khi nhận message RabbitMQ. */
@Configuration(proxyBeanMethods = false)
public class RabbitMqConfig {

  /** Dùng cùng JsonMapper của Booking để chuyển object và JSON message. */
  @Bean
  MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
  }

  /** Gắn handler lỗi vào listener container, giữ cơ chế reject native của Spring AMQP. */
  @Bean
  ContainerCustomizer<SimpleMessageListenerContainer> bookingRabbitContainerCustomizer() {
    return container ->
        container.setErrorHandler(
            new com.flashticket.booking_service.messaging.BookingRabbitErrorHandler());
  }
}
