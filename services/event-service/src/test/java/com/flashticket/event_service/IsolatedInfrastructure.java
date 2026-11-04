package com.flashticket.event_service;

import static org.mockito.Mockito.mock;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@TestConfiguration(proxyBeanMethods = false)
class IsolatedInfrastructure {
  @Bean
  JdbcTemplate isolatedJdbc() {
    return mock(JdbcTemplate.class);
  }

  @Bean
  RabbitTemplate isolatedRabbit() {
    return mock(RabbitTemplate.class);
  }

  @Bean
  PlatformTransactionManager isolatedTransactions() {
    return mock(PlatformTransactionManager.class);
  }
}
