package com.flashticket.payment_service.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Configuration(proxyBeanMethods = false)
public class PaymentDataSourceConfig {
  @Bean(defaultCandidate = false, destroyMethod = "close")
  @ConfigurationProperties("app.db.write")
  HikariDataSource writeDs() {
    return new HikariDataSource();
  }

  @Bean(defaultCandidate = false, destroyMethod = "close")
  @ConfigurationProperties("app.db.read")
  HikariDataSource readDs() {
    return new HikariDataSource();
  }

  @Bean
  @Primary
  DataSource dataSource(
      @Qualifier("writeDs") HikariDataSource writeDs,
      @Qualifier("readDs") HikariDataSource readDs) {
    var routing =
        new AbstractRoutingDataSource() {
          @Override
          protected Object determineCurrentLookupKey() {
            return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? "read"
                : "write";
          }
        };
    routing.setDefaultTargetDataSource(writeDs);
    routing.setTargetDataSources(Map.of("write", writeDs, "read", readDs));
    routing.afterPropertiesSet();
    // Hoãn lấy connection tới câu SQL, sau khi Spring đã đặt cờ readOnly của transaction.
    var lazy = new LazyConnectionDataSourceProxy(routing);
    lazy.checkDefaultConnectionProperties();
    return lazy;
  }

  @Bean
  @ConditionalOnEnabledHealthIndicator("db")
  DataSourceHealthIndicator dbHealthContributor(@Qualifier("writeDs") HikariDataSource writeDs) {
    return new DataSourceHealthIndicator(writeDs);
  }
}
