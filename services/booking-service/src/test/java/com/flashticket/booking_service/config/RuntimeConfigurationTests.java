package com.flashticket.booking_service.config;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.BookingServiceApplication;
import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.booking_service.messaging.BookingRabbitErrorHandler;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class RuntimeConfigurationTests {
  @Test
  void feignScanIsLimitedToInternalClients() {
    var scan = BookingServiceApplication.class.getAnnotation(EnableFeignClients.class);
    assertThat(scan).isNotNull();
    assertThat(scan.basePackages())
        .containsExactly("com.flashticket.booking_service.client.internal");
    assertThat(scan.defaultConfiguration()).isEmpty();
  }

  @Test
  void internalOAuth2PropertiesMatchTheCentralYaml() {
    configuredRunner()
        .withPropertyValues(
            "INTERNAL_SERVICE_CLIENT_SECRET=test-only",
            "KEYCLOAK_TOKEN_URI=http://127.0.0.1:1/token")
        .run(
            context -> {
              var environment = context.getEnvironment();
              String registration = "spring.security.oauth2.client.registration.internal.";
              assertThat(environment.getProperty(registration + "client-id"))
                  .isEqualTo("internal-service");
              assertThat(environment.getProperty(registration + "client-secret"))
                  .isEqualTo("test-only");
              assertThat(environment.getProperty(registration + "authorization-grant-type"))
                  .isEqualTo("client_credentials");
              assertThat(environment.getProperty(registration + "client-authentication-method"))
                  .isEqualTo("client_secret_basic");
              assertThat(
                      environment.getProperty(
                          "spring.security.oauth2.client.provider.keycloak.token-uri"))
                  .isEqualTo("http://127.0.0.1:1/token");
              assertThat(
                      environment.getProperty(
                          "spring.cloud.openfeign.oauth2.enabled", Boolean.class))
                  .isFalse();
              String config = "spring.cloud.openfeign.client.config.default.";
              assertThat(environment.getProperty(config + "connectTimeout", Integer.class))
                  .isEqualTo(2000);
              assertThat(environment.getProperty(config + "readTimeout", Integer.class))
                  .isEqualTo(5000);
              assertThat(environment.getProperty(config + "loggerLevel")).isEqualTo("none");
            });
  }

  private ApplicationContextRunner configuredRunner() {
    return new ApplicationContextRunner()
        .withInitializer(
            context -> {
              try {
                var path =
                    Path.of(System.getProperty("basedir", "."))
                        .resolve(
                            "../../infrastructure/config-server/src/main/resources/config/booking-service.yaml");
                for (var source :
                    new YamlPropertySourceLoader().load("booking", new FileSystemResource(path))) {
                  context.getEnvironment().getPropertySources().addLast(source);
                }
              } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
              }
            });
  }

  @Test
  void rabbitTemplateUsesTheConfiguredJsonConverterWithoutDeclaringTopology() {
    configuredRunner()
        .withConfiguration(
            AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class))
        .withUserConfiguration(BookingJsonConfig.class, RabbitMqConfig.class)
        .withPropertyValues("RABBITMQ_HOST=broker.example.test", "RABBITMQ_PASSWORD=test-only")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(MessageConverter.class);
              var converter = context.getBean(MessageConverter.class);
              assertThat(context.getBean(RabbitTemplate.class).getMessageConverter())
                  .isSameAs(converter);
              assertThat(context.getBeansOfType(Queue.class)).isEmpty();
              assertThat(context.getBeansOfType(Exchange.class)).isEmpty();
              assertThat(context.getBeansOfType(Binding.class)).isEmpty();
              assertThat(context.getBeansOfType(RabbitAdmin.class)).isEmpty();
              var endpoint = new SimpleRabbitListenerEndpoint();
              endpoint.setId("error-probe");
              endpoint.setQueueNames("booking.sales-configured.q");
              endpoint.setAutoStartup(false);
              endpoint.setMessageListener(message -> {});
              var container =
                  context
                      .getBean(SimpleRabbitListenerContainerFactory.class)
                      .createListenerContainer(endpoint);
              assertThat(container.getErrorHandler()).isInstanceOf(BookingRabbitErrorHandler.class);
              assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
              container.destroy();
              var message = converter.toMessage(purchase(), new MessageProperties());
              var template = context.getBean(RabbitTemplate.class);
              var connectionFactory = context.getBean(CachingConnectionFactory.class);
              assertThat(connectionFactory.isPublisherConfirms()).isTrue();
              assertThat(connectionFactory.isSimplePublisherConfirms()).isFalse();
              assertThat(connectionFactory.isPublisherReturns()).isTrue();
              assertThat(template.isMandatoryFor(message)).isTrue();
              assertThat(template.getExchange()).isEqualTo("business.ex");
              var listener = context.getBean(RabbitProperties.class).getListener().getSimple();
              assertThat(listener.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
              assertThat(listener.getPrefetch()).isEqualTo(20);
              message.getMessageProperties().setInferredArgumentType(PurchaseSnapshot.class);
              assertThat(message.getMessageProperties().getContentType())
                  .isEqualTo("application/json");
              assertThat(converter.fromMessage(message)).isEqualTo(purchase());
              var tree = context.getBean(JsonMapper.class).readTree(message.getBody());
              assertThat(tree.get("subtotal").asString()).isEqualTo("200");
              assertThat(tree.get("frozenAt").asString()).isEqualTo("2026-10-24T00:00:00Z");
            });
  }

  @Test
  void redisClientBindsExternalConnectionPropertiesWithoutConnecting() {
    configuredRunner()
        .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
        .withPropertyValues(
            "REDIS_HOST=cache.example.test", "REDIS_PORT=6380", "REDIS_PASSWORD=test-only")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(StringRedisTemplate.class);
              var factory = context.getBean(LettuceConnectionFactory.class);
              assertThat(factory.getHostName()).isEqualTo("cache.example.test");
              assertThat(factory.getPort()).isEqualTo(6380);
              assertThat(factory.getPassword()).isEqualTo("test-only");
              assertThat(factory.getClientConfiguration().getCommandTimeout())
                  .isEqualTo(Duration.ofSeconds(2));
              assertThat(
                      factory
                          .getClientConfiguration()
                          .getClientOptions()
                          .orElseThrow()
                          .getSocketOptions()
                          .getConnectTimeout())
                  .isEqualTo(Duration.ofSeconds(2));
              assertThat(context.getBean(StringRedisTemplate.class).getConnectionFactory())
                  .isSameAs(factory);
            });
  }
}
