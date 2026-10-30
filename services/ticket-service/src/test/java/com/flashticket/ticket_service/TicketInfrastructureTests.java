package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(OutputCaptureExtension.class)
class TicketInfrastructureTests {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RabbitAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  DataSourceAutoConfiguration.class))
          .withUserConfiguration(MessagingScan.class)
          .withInitializer(
              context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.addFirst(
                    new MapPropertySource(
                        "isolated-endpoints",
                        Map.ofEntries(
                            Map.entry(
                                "TICKET_DB_URL",
                                "jdbc:postgresql://127.0.0.1:1/ticket_db?currentSchema=ticket_schema"),
                            Map.entry("TICKET_DB_USERNAME", "ticket_admin"),
                            Map.entry("TICKET_DB_PASSWORD", "test-only-password"),
                            Map.entry("RABBITMQ_HOST", "127.0.0.1"),
                            Map.entry("RABBITMQ_PORT", "1"),
                            Map.entry("RABBITMQ_USERNAME", "test-only-user"),
                            Map.entry("RABBITMQ_PASSWORD", "test-only-password"))));
                try {
                  for (var source :
                      new YamlPropertySourceLoader()
                          .load(
                              "ticket",
                              new ClassPathResource("platform-config/ticket-service.yaml")))
                    sources.addLast(source);
                } catch (java.io.IOException exception) {
                  throw new IllegalStateException(exception);
                }
                ConfigurationPropertySources.attach(context.getEnvironment());
              });

  @Test
  void databasePoolUsesExistingOwnerWithoutOpeningAConnection() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          var pool = context.getBean(HikariDataSource.class);
          assertThat(pool.getConnectionInitSql()).isEqualTo("SET ROLE ticket_owner");
          assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
          assertThat(pool.getMinimumIdle()).isEqualTo(1);
          assertThat(pool.isRunning()).isFalse();
        });
  }

  @Test
  void brokerIsProvisionedExternallyAndListenersUseManualAck() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(AmqpAdmin.class);
          var connection = context.getBean(CachingConnectionFactory.class);
          assertThat(connection.isPublisherConfirms()).isTrue();
          assertThat(connection.isSimplePublisherConfirms()).isFalse();
          assertThat(connection.isPublisherReturns()).isTrue();
          var template = context.getBean(RabbitTemplate.class);
          assertThat(template.getExchange()).isEqualTo("business.ex");
          assertThat(template.isMandatoryFor(new Message(new byte[0], new MessageProperties())))
              .isTrue();
          var container =
              context.getBean(SimpleRabbitListenerContainerFactory.class).createListenerContainer();
          assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
        });
  }

  @Test
  void serviceSpecificBrokerEndpointOverridesSharedSettings() {
    runner
        .withPropertyValues("TICKET_RABBITMQ_HOST=ticket-broker.test", "TICKET_RABBITMQ_PORT=5678")
        .run(
            context -> {
              var connection = context.getBean(CachingConnectionFactory.class);
              assertThat(connection.getHost()).isEqualTo("ticket-broker.test");
              assertThat(connection.getPort()).isEqualTo(5678);
            });
  }

  @Test
  void converterReadsJsonUsingListenerTypeInsteadOfProducerJavaClass() {
    runner.run(
        context -> {
          assertThat(context.getBeansOfType(MessageConverter.class)).hasSize(1);
          var props = new MessageProperties();
          props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
          props.setInferredArgumentType(AccessFixture.class);
          props.setHeader("__TypeId__", "untrusted.producer.Class");
          var message =
              new Message(
                  "{\"eventId\":\"fixture-event\",\"sourceConfigVersion\":\"2\"}"
                      .getBytes(StandardCharsets.UTF_8),
                  props);
          assertThat(context.getBean(MessageConverter.class).fromMessage(message))
              .isEqualTo(new AccessFixture("fixture-event", "2"));
        });
  }

  @Test
  void fatalConversionWithXDeathIsRejectedForDlqButDatabaseErrorsAreNotClassifiedAsFatal(
      CapturedOutput output) {
    runner.run(
        context -> {
          var container =
              context.getBean(SimpleRabbitListenerContainerFactory.class).createListenerContainer();
          var props = new MessageProperties();
          props.setHeader("x-death", java.util.List.of(Map.of("queue", "ticket.issuance.q")));
          var message = new Message("sensitive-fixture".getBytes(StandardCharsets.UTF_8), props);
          var fatal =
              new ListenerExecutionFailedException(
                  "conversion failed", new MessageConversionException("invalid fixture"), message);
          assertThatThrownBy(() -> container.getErrorHandler().handleError(fatal))
              .isInstanceOf(AmqpRejectAndDontRequeueException.class);
          container
              .getErrorHandler()
              .handleError(
                  new ListenerExecutionFailedException(
                      "database unavailable",
                      new java.sql.SQLTransientConnectionException(),
                      message));
          assertThat(output.getAll())
              .doesNotContain("sensitive-fixture", "invalid fixture", "database unavailable");
        });
  }

  public record AccessFixture(String eventId, String sourceConfigVersion) {}

  @Configuration(proxyBeanMethods = false)
  @ComponentScan("com.flashticket.ticket_service.messaging")
  static class MessagingScan {}
}
