package com.flashticket.event_service.config;

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
public class EventDataSourceConfig {
  static final String REPLICA_READ_TRANSACTION = "eventReplicaRead";

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource eventWriterDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "event.read-replica", name = "enabled", havingValue = "true")
  @ConfigurationProperties("event.read-replica.datasource")
  HikariDataSource eventReaderDataSource() {
    return new HikariDataSource();
  }

  @Bean
  @Primary
  DataSource dataSource(
      @Qualifier("eventWriterDataSource") HikariDataSource writer,
      @Qualifier("eventReaderDataSource") ObjectProvider<HikariDataSource> readerProvider) {
    var reader = readerProvider.getIfAvailable();
    if (reader == null) return new LazyConnectionDataSourceProxy(writer);
    var routing = new EventRoutingDataSource();
    routing.setDefaultTargetDataSource(writer);
    routing.setTargetDataSources(Map.of("writer", writer, "reader", reader));
    routing.setLenientFallback(false);
    routing.afterPropertiesSet();
    // Lấy mặc định từ writer lúc khởi động; request chỉ acquire sau khi có marker transaction.
    var lazy = new LazyConnectionDataSourceProxy(routing);
    lazy.checkDefaultConnectionProperties();
    return lazy;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "event.read-replica", name = "enabled", havingValue = "true")
  TransactionTemplate eventReplicaReadTransactionTemplate(
      PlatformTransactionManager transactionManager,
      @Value("${event.read-replica.transaction-timeout}") int readerTransactionTimeoutSeconds) {
    if (readerTransactionTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("Replica transaction timeout must be positive");
    }
    var template = new TransactionTemplate(transactionManager);
    template.setName(REPLICA_READ_TRANSACTION);
    template.setReadOnly(true);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    template.setTimeout(readerTransactionTimeoutSeconds);
    return template;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "event.read-replica", name = "enabled", havingValue = "true")
  ThreadPoolTaskScheduler eventReplicaScheduler() {
    // Probe chờ DB không được chiếm scheduler outbox.
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("event-read-replica-");
    return scheduler;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "event.read-replica", name = "enabled", havingValue = "true")
  EventReplicaHealth eventReplicaHealth(
      @Qualifier("eventWriterDataSource") HikariDataSource writer,
      @Qualifier("eventReaderDataSource") HikariDataSource reader,
      @Value("${event.read-replica.max-sample-age}") Duration maxSampleAge,
      @Value("${event.read-replica.probe-query-timeout}") int probeQueryTimeoutSeconds,
      MeterRegistry metrics) {
    return new EventReplicaHealth(writer, reader, maxSampleAge, probeQueryTimeoutSeconds, metrics);
  }

  @Bean
  @ConditionalOnEnabledHealthIndicator("db")
  DataSourceHealthIndicator dbHealthContributor(
      @Qualifier("eventWriterDataSource") HikariDataSource writer) {
    return new DataSourceHealthIndicator(writer);
  }
}
