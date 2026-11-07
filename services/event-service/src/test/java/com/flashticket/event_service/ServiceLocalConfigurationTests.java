package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ServiceLocalConfigurationTests {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withPropertyValues(
              "spring.config.location=classpath:service-config/application.yaml",
              "CONFIG_SERVER_URL=http://127.0.0.1:1/config-server-must-not-be-used",
              "EVENT_DB_URL=jdbc:postgresql://127.0.0.1:1/event_db",
              "EVENT_DB_PASSWORD=fixture-db",
              "EVENT_RABBITMQ_HOST=127.0.0.1",
              "EVENT_RABBITMQ_PASSWORD=fixture-broker",
              "EVENT_KEYCLOAK_TOKEN_URI=http://127.0.0.1:1/token",
              "EVENT_INTERNAL_SERVICE_CLIENT_SECRET=fixture-oauth",
              "EVENT_KEYCLOAK_ISSUER_URI=http://127.0.0.1:1/realm",
              "EVENT_KEYCLOAK_JWK_SET_URI=http://127.0.0.1:1/jwks");

  @Test
  void bundledConfigurationLoadsWithoutConfigServerAndRetainsInfrastructureContracts() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          var env = context.getEnvironment();
          assertThat(env.getProperty("spring.datasource.url"))
              .isEqualTo("jdbc:postgresql://127.0.0.1:1/event_db");
          assertThat(env.getProperty("spring.datasource.hikari.connection-init-sql"))
              .isEqualTo("SET ROLE event_owner");
          assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
          assertThat(env.getProperty("spring.jpa.properties.hibernate.default_schema"))
              .isEqualTo("event_schema");
          assertThat(env.getProperty("spring.rabbitmq.dynamic", Boolean.class)).isFalse();
          assertThat(env.getProperty("spring.rabbitmq.publisher-confirm-type"))
              .isEqualTo("correlated");
          assertThat(env.getProperty("spring.rabbitmq.publisher-returns", Boolean.class)).isTrue();
          assertThat(env.getProperty("spring.rabbitmq.template.mandatory", Boolean.class)).isTrue();
          assertThat(env.getProperty("spring.rabbitmq.template.exchange")).isEqualTo("business.ex");
          assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.audiences[0]"))
              .isEqualTo("flash-ticket-api");
          assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
              .isEqualTo("http://127.0.0.1:1/jwks");
          assertThat(env.getProperty("spring.security.oauth2.client.provider.keycloak.token-uri"))
              .isEqualTo("http://127.0.0.1:1/token");
          assertThat(env.getProperty("event.outbox.enabled", Boolean.class)).isTrue();
          assertThat(env.getProperty("event.read-model.enabled", Boolean.class)).isFalse();
          assertThat(env.getProperty("eureka.client.register-with-eureka", Boolean.class)).isTrue();
          assertThat(env.getProperty("management.endpoints.web.exposure.include"))
              .isEqualTo("health,info");
          assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
          assertThat(env.getProperty("spring.servlet.multipart.max-file-size"))
              .isEqualTo("5242880B");
          assertThat(env.getProperty("server.tomcat.max-swallow-size")).isEqualTo("6291456B");
        });
  }

  @Test
  void scopedEnvironmentOverridesSharedFallbacksAndWorkerDefaults() {
    runner
        .withPropertyValues(
            "RABBITMQ_HOST=wrong-shared-host",
            "KEYCLOAK_JWK_SET_URI=http://wrong-shared-host/jwks",
            "EVENT_RABBITMQ_BUSINESS_EXCHANGE=fixture.business.ex",
            "EVENT_KEYCLOAK_AUDIENCE=fixture-audience",
            "EVENT_OUTBOX_ENABLED=false",
            "EVENT_READ_MODEL_ENABLED=true",
            "EVENT_IMAGES_MAX_BYTES=1234",
            "EVENT_IMAGES_MAX_REQUEST_BYTES=2345")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var env = context.getEnvironment();
              assertThat(env.getProperty("spring.rabbitmq.host")).isEqualTo("127.0.0.1");
              assertThat(env.getProperty("spring.rabbitmq.template.exchange"))
                  .isEqualTo("fixture.business.ex");
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.audiences[0]"))
                  .isEqualTo("fixture-audience");
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                  .isEqualTo("http://127.0.0.1:1/jwks");
              assertThat(env.getProperty("event.outbox.enabled", Boolean.class)).isFalse();
              assertThat(env.getProperty("event.read-model.enabled", Boolean.class)).isTrue();
              assertThat(env.getProperty("event.images.max-bytes", Integer.class)).isEqualTo(1234);
              assertThat(env.getProperty("spring.servlet.multipart.max-file-size"))
                  .isEqualTo("1234B");
              assertThat(env.getProperty("spring.servlet.multipart.max-request-size"))
                  .isEqualTo("2345B");
              assertThat(env.getProperty("server.tomcat.max-swallow-size")).isEqualTo("2345B");
            });
  }

  @Test
  void canonicalGenericEnvironmentVariablesSatisfyAllSharedEndpoints() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.config.location=classpath:service-config/application.yaml",
            "CONFIG_SERVER_URL=http://127.0.0.1:1/config-server-must-not-be-used",
            "EVENT_DB_URL=jdbc:postgresql://127.0.0.1:1/event_db",
            "EVENT_DB_PASSWORD=fixture-db",
            "RABBITMQ_HOST=canonical-broker",
            "RABBITMQ_PORT=5673",
            "RABBITMQ_USERNAME=canonical-user",
            "RABBITMQ_PASSWORD=canonical-secret",
            "RABBITMQ_VHOST=/canonical-vhost",
            "RABBITMQ_BUSINESS_EXCHANGE=canonical.business.ex",
            "KEYCLOAK_TOKEN_URI=http://127.0.0.1:1/canonical-token",
            "EVENT_INTERNAL_SERVICE_CLIENT_SECRET=fixture-oauth",
            "KEYCLOAK_ISSUER_URI=http://127.0.0.1:1/canonical-realm",
            "KEYCLOAK_JWK_SET_URI=http://127.0.0.1:1/canonical-jwks")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var env = context.getEnvironment();
              assertThat(env.getProperty("spring.rabbitmq.host")).isEqualTo("canonical-broker");
              assertThat(env.getProperty("spring.rabbitmq.port", Integer.class)).isEqualTo(5673);
              assertThat(env.getProperty("spring.rabbitmq.username")).isEqualTo("canonical-user");
              assertThat(env.getProperty("spring.rabbitmq.virtual-host"))
                  .isEqualTo("/canonical-vhost");
              assertThat(env.getProperty("spring.rabbitmq.template.exchange"))
                  .isEqualTo("canonical.business.ex");
              assertThat(
                      env.getProperty("spring.security.oauth2.client.provider.keycloak.token-uri"))
                  .isEqualTo("http://127.0.0.1:1/canonical-token");
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                  .isEqualTo("http://127.0.0.1:1/canonical-realm");
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                  .isEqualTo("http://127.0.0.1:1/canonical-jwks");
            });
  }
}
