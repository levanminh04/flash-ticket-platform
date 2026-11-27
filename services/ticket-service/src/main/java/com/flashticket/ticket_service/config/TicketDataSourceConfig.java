package com.flashticket.ticket_service.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
public class TicketDataSourceConfig {
  static final String LIST_TRANSACTION = "ticketListRead";

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource ticketWriterDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "ticket.list-replica", name = "enabled", havingValue = "true")
  @ConfigurationProperties("ticket.list-replica.datasource")
  HikariDataSource ticketListReaderDataSource() {
    return new HikariDataSource();
  }

  @Bean
  @Primary
  DataSource dataSource(
      @Qualifier("ticketWriterDataSource") HikariDataSource writer,
      @Qualifier("ticketListReaderDataSource") ObjectProvider<HikariDataSource> readerProvider) {
    var reader = readerProvider.getIfAvailable();
    if (reader == null) return new LazyConnectionDataSourceProxy(writer);
    var routing = new TicketRoutingDataSource();
    routing.setDefaultTargetDataSource(writer);
    routing.setTargetDataSources(Map.of("writer", writer, "reader", reader));
    routing.setLenientFallback(false);
    routing.afterPropertiesSet();
    // Lazy proxy dò defaults trước transaction; khi đó router vẫn chọn writer.
    return new LazyConnectionDataSourceProxy(routing);
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "ticket.list-replica", name = "enabled", havingValue = "true")
  TransactionTemplate ticketListReadTransactionTemplate(
      PlatformTransactionManager transactionManager,
      @Value("${ticket.list-replica.reader-transaction-timeout}") int timeoutSeconds) {
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("Replica transaction timeout must be positive");
    }
    var template = new TransactionTemplate(transactionManager);
    template.setName(LIST_TRANSACTION);
    template.setReadOnly(true);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    template.setTimeout(timeoutSeconds);
    return template;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "ticket.list-replica", name = "enabled", havingValue = "true")
  ThreadPoolTaskScheduler ticketListReplicaScheduler() {
    // Probe có thể chờ DB, không dùng ba thread của worker nghiệp vụ.
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("ticket-list-replica-");
    return scheduler;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "ticket.list-replica", name = "enabled", havingValue = "true")
  TicketListReplicaHealth ticketListReplicaHealth(
      @Qualifier("ticketWriterDataSource") HikariDataSource writer,
      @Qualifier("ticketListReaderDataSource") HikariDataSource reader,
      @Value("${ticket.list-replica.max-sample-age}") Duration maxSampleAge,
      @Value("${ticket.list-replica.probe-query-timeout}") int probeQueryTimeoutSeconds,
      MeterRegistry metrics) {
    return new TicketListReplicaHealth(
        writer, reader, maxSampleAge, probeQueryTimeoutSeconds, metrics);
  }

  @Bean
  @ConditionalOnEnabledHealthIndicator("db")
  DataSourceHealthIndicator dbHealthContributor(
      @Qualifier("ticketWriterDataSource") HikariDataSource writer) {
    return new DataSourceHealthIndicator(writer);
  }
}
