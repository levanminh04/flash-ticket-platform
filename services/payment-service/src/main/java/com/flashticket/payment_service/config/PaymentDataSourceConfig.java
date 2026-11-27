package com.flashticket.payment_service.config;

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
public class PaymentDataSourceConfig {
  static final String REPORT_TRANSACTION = "paymentReportRead";

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource paymentWriterDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "payment.report-replica", name = "enabled", havingValue = "true")
  @ConfigurationProperties("payment.report-replica.datasource")
  HikariDataSource paymentReportReaderDataSource() {
    return new HikariDataSource();
  }

  @Bean
  @Primary
  DataSource dataSource(
      @Qualifier("paymentWriterDataSource") HikariDataSource writer,
      @Qualifier("paymentReportReaderDataSource") ObjectProvider<HikariDataSource> readerProvider) {
    var reader = readerProvider.getIfAvailable();
    if (reader == null) return new LazyConnectionDataSourceProxy(writer);
    var routing = new PaymentRoutingDataSource();
    routing.setDefaultTargetDataSource(writer);
    routing.setTargetDataSources(Map.of("writer", writer, "reader", reader));
    routing.afterPropertiesSet();
    // Khởi tạo mặc định writer trước khi request có marker reader tới JPA.
    var lazy = new LazyConnectionDataSourceProxy(routing);
    lazy.checkDefaultConnectionProperties();
    return lazy;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "payment.report-replica", name = "enabled", havingValue = "true")
  TransactionTemplate paymentReportReadTransactionTemplate(
      PlatformTransactionManager transactionManager,
      @Value("${payment.report-replica.reader-transaction-timeout}")
          int readerTransactionTimeoutSeconds) {
    if (readerTransactionTimeoutSeconds <= 0)
      throw new IllegalArgumentException("Replica transaction timeout must be positive");
    var template = new TransactionTemplate(transactionManager);
    template.setName(REPORT_TRANSACTION);
    template.setReadOnly(true);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    template.setTimeout(readerTransactionTimeoutSeconds);
    return template;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "payment.report-replica", name = "enabled", havingValue = "true")
  ThreadPoolTaskScheduler paymentReportReplicaScheduler() {
    // Probe chờ DB không được giữ scheduler worker nghiệp vụ.
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("payment-report-replica-");
    return scheduler;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(prefix = "payment.report-replica", name = "enabled", havingValue = "true")
  PaymentReportReplicaHealth paymentReportReplicaHealth(
      @Qualifier("paymentWriterDataSource") HikariDataSource writer,
      @Qualifier("paymentReportReaderDataSource") HikariDataSource reader,
      @Value("${payment.report-replica.max-sample-age}") Duration maxSampleAge,
      @Value("${payment.report-replica.probe-query-timeout}") int probeQueryTimeoutSeconds,
      MeterRegistry metrics) {
    return new PaymentReportReplicaHealth(
        writer, reader, maxSampleAge, probeQueryTimeoutSeconds, metrics);
  }

  @Bean
  @ConditionalOnEnabledHealthIndicator("db")
  DataSourceHealthIndicator dbHealthContributor(
      @Qualifier("paymentWriterDataSource") HikariDataSource writer) {
    return new DataSourceHealthIndicator(writer);
  }
}
