package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashticket.ticket_service.config.TicketBusinessConfiguration;
import com.flashticket.ticket_service.config.TicketDataSourceConfig;
import com.flashticket.ticket_service.config.TicketListReplicaHealth;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

class TicketLocalConfigurationTests {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withInitializer(
              context ->
                  context
                      .getBeanFactory()
                      .setConversionService(ApplicationConversionService.getSharedInstance()))
          .withUserConfiguration(TicketDataSourceConfig.class)
          .withConfiguration(
              AutoConfigurations.of(
                  DataSourceAutoConfiguration.class, RabbitAutoConfiguration.class))
          .withPropertyValues(
              "spring.config.location=classpath:service-config/application.yaml",
              "CONFIG_SERVER_URL=http://127.0.0.1:1/unavailable",
              "TICKET_DB_URL=jdbc:postgresql://127.0.0.1:1/isolated",
              "TICKET_DB_PASSWORD=fixture-only",
              "TICKET_RABBITMQ_HOST=127.0.0.1",
              "TICKET_RABBITMQ_PORT=1",
              "TICKET_RABBITMQ_PASSWORD=fixture-only",
              "TICKET_KEYCLOAK_ISSUER_URI=https://issuer.invalid/realm",
              "TICKET_KEYCLOAK_JWK_SET_URI=https://issuer.invalid/certs",
              "TICKET_KEYCLOAK_AUDIENCE=ticket-fixture-api",
              "MAIL_HOST=smtp.invalid",
              "MAIL_PORT=587",
              "MAIL_USERNAME=fixture@example.test",
              "MAIL_PASSWORD=fixture-only",
              "EUREKA_CLIENT_ENABLED=false");

  @Test
  void productionYamlBootstrapsWithoutConfigServerAndRetainsInfrastructurePolicies() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          var env = context.getEnvironment();
          assertThat(env.getProperty("spring.application.name")).isEqualTo("ticket-service");
          assertThat(env.getProperty("spring.config.import")).isNull();
          assertThat(
                  ClassUtils.isPresent(
                      "org.springframework.cloud.config.client.ConfigClientProperties",
                      getClass().getClassLoader()))
              .isFalse();
          assertThat(env.getProperty("spring.sql.init.mode")).isEqualTo("never");
          assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
          assertThat(env.getProperty("spring.jpa.properties.hibernate.default_schema"))
              .isEqualTo("ticket_schema");
          var pool = context.getBean(HikariDataSource.class);
          assertThat(pool.getConnectionInitSql()).isEqualTo("SET ROLE ticket_owner");
          assertThat(pool.isRunning()).isFalse();
          var rabbit = context.getBean(CachingConnectionFactory.class);
          assertThat(rabbit.isPublisherConfirms()).isTrue();
          assertThat(rabbit.isPublisherReturns()).isTrue();
          assertThat(env.getProperty("spring.rabbitmq.dynamic")).isEqualTo("false");
          assertThat(env.getProperty("spring.rabbitmq.template.exchange")).isEqualTo("business.ex");
          assertThat(env.getProperty("spring.rabbitmq.template.mandatory")).isEqualTo("true");
          assertThat(env.getProperty("spring.rabbitmq.listener.simple.acknowledge-mode"))
              .isEqualTo("manual");
          assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.audiences[0]"))
              .isEqualTo("ticket-fixture-api");
          assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
              .isEqualTo("https://issuer.invalid/realm");
          assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
              .isEqualTo("https://issuer.invalid/certs");
          assertThat(env.getProperty("logging.structured.json.customizer"))
              .isEqualTo("com.flashticket.ticket_service.config.TicketLogFields");
          assertThat(env.getProperty("spring.mail.properties[mail.smtp.starttls.required]"))
              .isEqualTo("true");
          assertThat(env.getProperty("management.endpoints.web.exposure.include"))
              .isEqualTo("health,info");
          assertThat(env.getProperty("eureka.client.service-url.defaultZone"))
              .isEqualTo("http://localhost:8761/eureka/");
          assertThat(env.getProperty("ticket.features.business-enabled")).isEqualTo("false");
          assertThat(env.getProperty("ticket.features.listeners-enabled")).isEqualTo("false");
          assertThat(env.getProperty("ticket.features.workers-enabled")).isEqualTo("false");
        });
  }

  @Test
  void replicaOffNeedsNoReaderPropertiesAndUsesOnlyWriterHealth() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getEnvironment().getProperty("ticket.list-replica.enabled"))
              .isEqualTo("false");
          assertThat(context).doesNotHaveBean("ticketListReaderDataSource");
          assertThat(context).doesNotHaveBean("ticketListReadTransactionTemplate");
          assertThat(context).doesNotHaveBean("ticketListReplicaHealth");
          assertThat(context).doesNotHaveBean("ticketListReplicaScheduler");
          var writer = context.getBean("ticketWriterDataSource", HikariDataSource.class);
          var lazy = (LazyConnectionDataSourceProxy) context.getBean(DataSource.class);
          assertThat(lazy.getTargetDataSource()).isSameAs(writer);
          var health = context.getBean("dbHealthContributor", DataSourceHealthIndicator.class);
          assertThat(ReflectionTestUtils.getField(health, "dataSource")).isSameAs(writer);
          assertThat(writer.isRunning()).isFalse();
        });
  }

  @Test
  void replicaRequiresPositiveTimingAndConditionalBeans() {
    var enabled =
        runner
            .withUserConfiguration(TicketBusinessConfiguration.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues(
                "TICKET_BUSINESS_ENABLED=true",
                "TICKET_LIST_REPLICA_ENABLED=true",
                "TICKET_LIST_REPLICA_JDBC_URL=jdbc:postgresql://127.0.0.1:1/ticket_test_c1?targetServerType=secondary",
                "TICKET_LIST_REPLICA_USERNAME=ticket_reader",
                "TICKET_LIST_REPLICA_PASSWORD=fixture-only",
                "TICKET_LIST_REPLICA_PROBE_INTERVAL=PT1H");
    enabled.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasBean("ticketListReadTransactionTemplate");
          assertThat(context).hasSingleBean(PlatformTransactionManager.class);
          var template =
              context.getBean("ticketListReadTransactionTemplate", TransactionTemplate.class);
          assertThat(template.getName()).isEqualTo("ticketListRead");
          assertThat(template.isReadOnly()).isTrue();
          assertThat(template.getIsolationLevel())
              .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
          assertThat(template.getPropagationBehavior())
              .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
          assertThat(template.getTimeout()).isEqualTo(5);
          var reader = context.getBean("ticketListReaderDataSource", HikariDataSource.class);
          assertThat(reader.getMaximumPoolSize()).isEqualTo(2);
          assertThat(reader.getMinimumIdle()).isZero();
          assertThat(reader.getConnectionTimeout()).isEqualTo(5000);
          assertThat(reader.isReadOnly()).isTrue();
          assertThat(reader.getConnectionInitSql()).isNull();
          assertThat(reader.getUsername()).isEqualTo("ticket_reader");
          assertThat(reader.getDataSourceProperties())
              .containsEntry("connectTimeout", "5")
              .containsEntry("socketTimeout", "5");
          assertThat(reader.isRunning()).isFalse();
          assertThat(
                  context
                      .getBean("ticketListReplicaScheduler", ThreadPoolTaskScheduler.class)
                      .getPoolSize())
              .isEqualTo(1);
          var health = context.getBean(TicketListReplicaHealth.class);
          assertThat(health.tryBeginRead()).isFalse();
          var writer = context.getBean("ticketWriterDataSource", HikariDataSource.class);
          assertThat(writer.getMaximumPoolSize()).isEqualTo(5);
          assertThat(writer.getMinimumIdle()).isEqualTo(1);
          assertThat(writer.getConnectionInitSql()).isEqualTo("SET ROLE ticket_owner");
          var dbHealth = context.getBean("dbHealthContributor", DataSourceHealthIndicator.class);
          assertThat(ReflectionTestUtils.getField(dbHealth, "dataSource")).isSameAs(writer);
          var lazy = (LazyConnectionDataSourceProxy) context.getBean(DataSource.class);
          var routing = (AbstractRoutingDataSource) lazy.getTargetDataSource();
          assertThat(ReflectionTestUtils.getField(routing, "lenientFallback")).isEqualTo(false);
          // Không mượn connection: kiểm whitelist trên metadata transaction, chưa chứng minh DB
          // thật.
          try {
            for (String name : new String[] {"ticketListRead", "other", ""}) {
              TransactionSynchronizationManager.setCurrentTransactionName(name);
              for (boolean active : new boolean[] {false, true}) {
                TransactionSynchronizationManager.setActualTransactionActive(active);
                for (boolean readOnly : new boolean[] {false, true}) {
                  TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);
                  Object selected =
                      ReflectionTestUtils.invokeMethod(routing, "determineCurrentLookupKey");
                  assertThat(selected)
                      .isEqualTo(
                          active && readOnly && name.equals("ticketListRead")
                              ? "reader"
                              : "writer");
                }
              }
            }
          } finally {
            TransactionSynchronizationManager.clear();
          }
        });
    for (String invalid :
        new String[] {
          "TICKET_LIST_REPLICA_READER_TRANSACTION_TIMEOUT=0",
          "TICKET_LIST_REPLICA_READER_TRANSACTION_TIMEOUT=-1",
          "TICKET_LIST_REPLICA_PROBE_QUERY_TIMEOUT=0",
          "TICKET_LIST_REPLICA_MAX_SAMPLE_AGE=PT0S",
          "TICKET_LIST_REPLICA_MAX_SAMPLE_AGE=-PT1S",
          "TICKET_LIST_REPLICA_PROBE_INTERVAL=PT0S"
        }) {
      enabled.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
    }
  }

  @Test
  void serviceEnvironmentSelectsTheSameSagaQrAndMailContractWithoutRemoteConfiguration() {
    runner
        .withPropertyValues(
            "TICKET_BUSINESS_ENABLED=true",
            "TICKET_LISTENERS_ENABLED=true",
            "TICKET_WORKERS_ENABLED=true",
            "TICKET_QR_ACTIVE_KEY_VERSION=fixture-v1",
            "TICKET_QR_KEY_RING_LOCATION=file:/run/ticket-qr/keys.json",
            "TICKET_MAIL_FROM=tickets@example.test")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var env = context.getEnvironment();
              assertThat(env.getProperty("ticket.messaging.completed-result-message-type"))
                  .isEqualTo("ticket.issuance-result.v1");
              assertThat(env.getProperty("ticket.messaging.completed-result-routing-key"))
                  .isEqualTo("ticket.issuance-result.v1");
              assertThat(env.getProperty("ticket.messaging.failed-result-routing-key"))
                  .isEqualTo("ticket.issuance-result.v1");
              assertThat(env.getProperty("ticket.qr.active-key-version")).isEqualTo("fixture-v1");
              assertThat(env.getProperty("ticket.qr.key-ring-location"))
                  .isEqualTo("file:/run/ticket-qr/keys.json");
              assertThat(env.getProperty("ticket.delivery.from")).isEqualTo("tickets@example.test");
              assertThat(env.getProperty("ticket.delivery.buyer-base-url")).isEmpty();
              assertThat(env.getProperty("ticket.features.workers-enabled")).isEqualTo("true");
            });
  }
}
