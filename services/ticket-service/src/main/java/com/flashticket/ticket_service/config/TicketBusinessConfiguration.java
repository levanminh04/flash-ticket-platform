package com.flashticket.ticket_service.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "ticket.features", name = "business-enabled", havingValue = "true")
public class TicketBusinessConfiguration {
  @Bean
  JdbcTransactionManager ticketTransactionManager(DataSource datasource) {
    return new JdbcTransactionManager(datasource);
  }

  @Bean
  JsonMapperBuilderCustomizer ticketJson() {
    return builder ->
        builder
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }
}
