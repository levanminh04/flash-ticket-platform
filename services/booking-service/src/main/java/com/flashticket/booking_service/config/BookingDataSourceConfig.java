package com.flashticket.booking_service.config;

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
public class BookingDataSourceConfig {
  static final String ADMIN_CHART_TRANSACTION = "bookingAdminChartRead";
  static final String SALES_REPORT_TRANSACTION = "bookingSalesReportRead";

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource bookingWriterDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(
      prefix = "booking.sales-chart-replica",
      name = "enabled",
      havingValue = "true")
  @ConfigurationProperties("booking.sales-chart-replica.datasource")
  HikariDataSource bookingChartReaderDataSource() {
    return new HikariDataSource();
  }

  @Bean
  @Primary
  DataSource dataSource(
      @Qualifier("bookingWriterDataSource") HikariDataSource writer,
      @Qualifier("bookingChartReaderDataSource") ObjectProvider<HikariDataSource> readerProvider) {
    var reader = readerProvider.getIfAvailable();
    if (reader == null) return new LazyConnectionDataSourceProxy(writer);

    var routing = new BookingRoutingDataSource();
    routing.setDefaultTargetDataSource(writer);
    routing.setTargetDataSources(Map.of("writer", writer, "reader", reader));
    routing.afterPropertiesSet();

    // Đọc mặc định của writer lúc khởi động, trước khi JPA mở transaction biểu đồ.
    var lazy = new LazyConnectionDataSourceProxy(routing);
    lazy.checkDefaultConnectionProperties();
    return lazy;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(
      prefix = "booking.sales-chart-replica",
      name = "enabled",
      havingValue = "true")
  TransactionTemplate bookingAdminChartReadTransactionTemplate(
      PlatformTransactionManager transactionManager,
      @Value("${booking.sales-chart-replica.reader-transaction-timeout}")
          int readerTransactionTimeoutSeconds) {
    if (readerTransactionTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("Replica transaction timeout must be positive");
    }
    var template = new TransactionTemplate(transactionManager);
    template.setName(ADMIN_CHART_TRANSACTION);
    template.setReadOnly(true);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    template.setTimeout(readerTransactionTimeoutSeconds);
    return template;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(
      prefix = "booking.sales-chart-replica",
      name = "enabled",
      havingValue = "true")
  TransactionTemplate bookingSalesReportReadTransactionTemplate(
      PlatformTransactionManager transactionManager,
      @Value("${booking.sales-chart-replica.reader-transaction-timeout}")
          int readerTransactionTimeoutSeconds) {
    if (readerTransactionTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("Replica transaction timeout must be positive");
    }
    var template = new TransactionTemplate(transactionManager);
    template.setName(SALES_REPORT_TRANSACTION);
    template.setReadOnly(true);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    template.setTimeout(readerTransactionTimeoutSeconds);
    return template;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(
      prefix = "booking.sales-chart-replica",
      name = "enabled",
      havingValue = "true")
  ThreadPoolTaskScheduler bookingChartReplicaScheduler() {
    // Probe có thể chờ DB; không dùng chung scheduler với worker nghiệp vụ.
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("booking-chart-replica-");
    return scheduler;
  }

  @Bean(defaultCandidate = false)
  @ConditionalOnProperty(
      prefix = "booking.sales-chart-replica",
      name = "enabled",
      havingValue = "true")
  BookingChartReplicaHealth bookingChartReplicaHealth(
      @Qualifier("bookingWriterDataSource") HikariDataSource writer,
      @Qualifier("bookingChartReaderDataSource") HikariDataSource reader,
      @Value("${booking.sales-chart-replica.max-sample-age}") Duration maxSampleAge,
      @Value("${booking.sales-chart-replica.probe-query-timeout}") int probeQueryTimeoutSeconds,
      MeterRegistry metrics) {
    return new BookingChartReplicaHealth(
        writer, reader, maxSampleAge, probeQueryTimeoutSeconds, metrics);
  }

  @Bean
  @ConditionalOnEnabledHealthIndicator("db")
  DataSourceHealthIndicator dbHealthContributor(
      @Qualifier("bookingWriterDataSource") HikariDataSource writer) {
    return new DataSourceHealthIndicator(writer);
  }
}
