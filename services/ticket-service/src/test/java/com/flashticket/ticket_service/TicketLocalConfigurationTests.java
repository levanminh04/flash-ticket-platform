package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;

class TicketLocalConfigurationTests {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
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
          assertThat(env.getProperty("logging.structured.json.add.serviceName")).isNull();
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
