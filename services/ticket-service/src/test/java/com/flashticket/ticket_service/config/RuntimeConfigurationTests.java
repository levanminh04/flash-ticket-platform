package com.flashticket.ticket_service.config;

import static com.flashticket.ticket_service.support.TicketFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.ticket_service.messaging.TicketRabbitErrorHandler;
import java.nio.file.Path;
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
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.json.JsonMapper;

class RuntimeConfigurationTests {
  private ApplicationContextRunner configuredRunner() {
    return new ApplicationContextRunner()
        .withInitializer(
            context -> {
              try {
                var path =
                    Path.of(System.getProperty("basedir", "."))
                        .resolve(
                            "../../infrastructure/config-server/src/main/resources/config/ticket-service.yaml");
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
        .withUserConfiguration(TicketJsonConfig.class, RabbitMqConfig.class)
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
              endpoint.setQueueNames("ticket.issuance.q");
              endpoint.setAutoStartup(false);
              endpoint.setMessageListener(message -> {});
              var container =
                  context
                      .getBean(SimpleRabbitListenerContainerFactory.class)
                      .createListenerContainer(endpoint);
              assertThat(container.getErrorHandler()).isInstanceOf(TicketRabbitErrorHandler.class);
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
              assertThat(tree.get("frozenAt").asString()).isEqualTo("2026-10-26T12:00:00Z");
            });
  }

  @Test
  void converterUsesTheApplicationJsonPolicy() {
    var converter = new RabbitMqConfig().rabbitMessageConverter(jsonMapper());
    var message = converter.toMessage(completed(), new MessageProperties());
    assertThat(jsonMapper().readTree(message.getBody()).get("ticketCount").asInt()).isEqualTo(2);
  }

  @Test
  void rejectsMalformedJsonWithSharedConverter() {
    var converter = new RabbitMqConfig().rabbitMessageConverter(jsonMapper());
    var properties = new MessageProperties();
    properties.setContentType("application/json");
    properties.setInferredArgumentType(
        com.flashticket.ticket_service.dto.message.IssueTickets.class);
    String input =
        jsonMapper().writeValueAsString(issue()).replace("\"quantity\":2", "\"quantity\":1.5");
    var message = new Message(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), properties);
    assertThatThrownBy(() -> converter.fromMessage(message))
        .isInstanceOf(org.springframework.amqp.support.converter.MessageConversionException.class);
  }

  @Test
  void persistenceJwtAndMailBindExternalValues() {
    configuredRunner()
        .withPropertyValues(
            "TICKET_DB_URL=jdbc:postgresql://127.0.0.1:15432/ticket_db?currentSchema=ticket_schema",
            "TICKET_DB_PASSWORD=test-only",
            "KEYCLOAK_ISSUER_URI=https://issuer.example.test/realms/ticket",
            "KEYCLOAK_JWK_SET_URI=https://issuer.example.test/jwks",
            "MAIL_HOST=mail.example.test",
            "MAIL_PORT=587",
            "MAIL_USERNAME=test-user",
            "MAIL_PASSWORD=test-only")
        .run(
            context -> {
              var env = context.getEnvironment();
              assertThat(env.getProperty("spring.datasource.url"))
                  .contains("ticket_db?currentSchema=ticket_schema");
              assertThat(env.getProperty("spring.datasource.username")).isEqualTo("ticket_admin");
              assertThat(env.getProperty("spring.datasource.password")).isEqualTo("test-only");
              assertThat(env.getProperty("spring.datasource.hikari.connection-init-sql"))
                  .isEqualTo("SET ROLE ticket_owner");
              assertThat(
                      env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                  .isEqualTo(5);
              assertThat(env.getProperty("spring.datasource.hikari.minimum-idle", Integer.class))
                  .isEqualTo(1);
              assertThat(env.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
              assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
              assertThat(env.getProperty("spring.jpa.properties.hibernate.default_schema"))
                  .isEqualTo("ticket_schema");
              assertThat(env.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"))
                  .isEqualTo("UTC");
              assertThat(env.getProperty("spring.jpa.properties.hibernate.type.json_format_mapper"))
                  .isEqualTo("jackson3");
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                  .isEqualTo("https://issuer.example.test/realms/ticket");
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.audiences[0]"))
                  .isEqualTo("flash-ticket-api");
              assertThat(env.getProperty("spring.mail.host")).isEqualTo("mail.example.test");
            });
  }
}
