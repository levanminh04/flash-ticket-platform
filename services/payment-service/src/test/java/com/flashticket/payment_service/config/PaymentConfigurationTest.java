package com.flashticket.payment_service.config;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.*;
import org.springframework.core.io.*;

class PaymentConfigurationTest {
  private StandardEnvironment configuration(boolean local) throws Exception {
    var env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "testInputs",
                Map.of(
                    "PAYMENT_DB_URL",
                    "jdbc:postgresql://localhost/test",
                    "PAYMENT_DB_USERNAME",
                    "fixture",
                    "PAYMENT_DB_PASSWORD",
                    "fixture-only")));
    var loader = new YamlPropertySourceLoader();
    for (var ps :
        loader.load(
            "payment",
            new FileSystemResource(
                "../../infrastructure/config-server/src/main/resources/config/payment-service.yaml")))
      env.getPropertySources().addLast(ps);
    for (var ps :
        loader.load(
            "paymentDefaults", new FileSystemResource("src/main/resources/application.yaml")))
      env.getPropertySources().addLast(ps);
    if (local)
      for (var ps :
          loader.load("local", new ClassPathResource("application-local-foundation.yaml")))
        env.getPropertySources().addFirst(ps);
    return env;
  }

  @Test
  void bindsDatabaseWithoutAllowingSchemaMutation() throws Exception {
    var env = configuration(false);
    assertThat(env.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://localhost/test");
    assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(env.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    assertThat(env.getProperty("spring.jpa.properties.hibernate.default_schema"))
        .isEqualTo("payment_schema");
    assertThat(env.getProperty("spring.sql.init.mode")).isEqualTo("never");
    assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(8083);
    assertThat(env.getProperty("spring.rabbitmq.dynamic", Boolean.class)).isFalse();
    var topology =
        tools.jackson.databind.json.JsonMapper.builder()
            .build()
            .readTree(new java.io.File("../../deploy/rabbitmq/definitions.json"));
    var queues = new java.util.HashSet<String>();
    for (var queue : topology.get("queues")) queues.add(queue.get("name").asText());
    assertThat(queues)
        .contains(
            env.getProperty("payment.queues.decision"),
            env.getProperty("payment.queues.issuance"),
            env.getProperty("payment.queues.completed"),
            env.getProperty("payment.queues.event-cancelled"),
            env.getProperty("payment.queues.finance-configured"));
    var exchanges = new java.util.HashSet<String>();
    for (var exchange : topology.get("exchanges")) exchanges.add(exchange.get("name").asText());
    assertThat(exchanges).contains(env.getProperty("payment.exchange"));
    assertThat(env.getProperty("payment.exchange"))
        .isEqualTo(env.getProperty("spring.rabbitmq.template.exchange"));
  }

  @Test
  void localProfileKeepsDatasourceAndDisablesUnimplementedIntegrations() throws Exception {
    var env = configuration(true);
    assertThat(env.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://localhost/test");
    assertThat(env.getProperty("eureka.client.enabled", Boolean.class)).isFalse();
    assertThat(env.getProperty("spring.rabbitmq.listener.simple.auto-startup", Boolean.class))
        .isFalse();
    assertThat(env.getProperty("spring.rabbitmq.listener.direct.auto-startup", Boolean.class))
        .isFalse();
    assertThat(env.getProperty("management.health.rabbit.enabled", Boolean.class)).isFalse();
  }
}
