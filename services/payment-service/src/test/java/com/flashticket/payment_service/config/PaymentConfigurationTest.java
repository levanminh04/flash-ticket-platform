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
