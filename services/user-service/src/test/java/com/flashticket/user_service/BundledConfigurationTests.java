package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.ClassUtils;

class BundledConfigurationTests {
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({RabbitProperties.class, OAuth2ResourceServerProperties.class})
  static class ConfigurationProbe {}

  private ApplicationContextRunner bundledConfiguration(Map<String, Object> overrides) {
    Map<String, Object> environment =
        new HashMap<>(
            Map.of(
                "MONGO_URI", "mongodb://127.0.0.1:1/fixture",
                "RABBITMQ_HOST", "127.0.0.1",
                "RABBITMQ_PASSWORD", "fixture-only",
                "KEYCLOAK_ISSUER_URI", "https://fixture.invalid/realm",
                "KEYCLOAK_JWK_SET_URI", "https://fixture.invalid/jwks"));
    environment.putAll(overrides);
    return new ApplicationContextRunner()
        .withPropertyValues("spring.config.location=classpath:service-config/application.yaml")
        .withInitializer(
            context -> {
              context
                  .getEnvironment()
                  .getPropertySources()
                  .addFirst(new SystemEnvironmentPropertySource("fixtureEnvironment", environment));
              new ConfigDataApplicationContextInitializer().initialize(context);
            })
        .withUserConfiguration(ConfigurationProbe.class);
  }

  @Test
  void productionConfigurationBindsWithoutConfigServerOrExternalInfrastructure() {
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.cloud.config.client.ConfigClientProperties",
                getClass().getClassLoader()))
        .isFalse();
    bundledConfiguration(Map.of())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var environment = context.getEnvironment();
              var rabbit = context.getBean(RabbitProperties.class);
              var jwt = context.getBean(OAuth2ResourceServerProperties.class).getJwt();
              assertThat(rabbit.getHost()).isEqualTo("127.0.0.1");
              assertThat(environment.getProperty("spring.rabbitmq.dynamic", Boolean.class))
                  .isFalse();
              assertThat(rabbit.getListener().getSimple().getAcknowledgeMode().name())
                  .isEqualTo("MANUAL");
              assertThat(rabbit.getListener().getSimple().getPrefetch()).isEqualTo(20);
              assertThat(jwt.getIssuerUri()).isEqualTo("https://fixture.invalid/realm");
              assertThat(jwt.getJwkSetUri()).isEqualTo("https://fixture.invalid/jwks");
              assertThat(jwt.getAudiences()).containsExactly("flash-ticket-api");
              assertThat(environment.getProperty("spring.mongodb.uri"))
                  .isEqualTo("mongodb://127.0.0.1:1/fixture");
              assertThat(environment.getProperty("user.identity.enabled", Boolean.class)).isTrue();
              assertThat(environment.getProperty("user.recovery.enabled", Boolean.class)).isTrue();
              assertThat(
                      environment.getProperty("eureka.client.register-with-eureka", Boolean.class))
                  .isTrue();
              assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                  .isEqualTo("health,info");
              assertThat(environment.getProperty("management.endpoint.health.show-details"))
                  .isEqualTo("never");
              assertThat(environment.getProperty("logging.structured.json.customizer"))
                  .isEqualTo("com.flashticket.user_service.observability.UserLogCustomizer");
              assertThat(environment.getProperty("logging.structured.json.include"))
                  .contains("correlationId");
            });
  }

  @Test
  void scopedRuntimeEnvironmentOverridesFallbackAndDisablesBackgroundWork() {
    bundledConfiguration(
            Map.of(
                "USER_MONGO_URI", "mongodb://127.0.0.1:2/scoped",
                "USER_RABBITMQ_HOST", "scoped-broker.invalid",
                "USER_RABBITMQ_PORT", "5673",
                "USER_KEYCLOAK_AUDIENCE", "scoped-audience",
                "USER_IDENTITY_ENABLED", "false",
                "USER_RECOVERY_ENABLED", "false"))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(RabbitProperties.class).getHost())
                  .isEqualTo("scoped-broker.invalid");
              assertThat(context.getBean(RabbitProperties.class).getPort()).isEqualTo(5673);
              assertThat(
                      context.getBean(OAuth2ResourceServerProperties.class).getJwt().getAudiences())
                  .containsExactly("scoped-audience");
              assertThat(context.getEnvironment().getProperty("spring.mongodb.uri"))
                  .isEqualTo("mongodb://127.0.0.1:2/scoped");
              assertThat(
                      context.getEnvironment().getProperty("user.identity.enabled", Boolean.class))
                  .isFalse();
              assertThat(
                      context.getEnvironment().getProperty("user.recovery.enabled", Boolean.class))
                  .isFalse();
            });
  }
}
